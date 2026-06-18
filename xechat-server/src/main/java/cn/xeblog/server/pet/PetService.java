package cn.xeblog.server.pet;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.PetAdoptDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.entity.pet.PetRaceResultDTO;
import cn.xeblog.server.account.DbInitializer;
import org.apache.ibatis.session.SqlSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PetService {

    private static final int DEFAULT_STAT = 30;
    private static final int DEFAULT_ENERGY = 10;

    private PetService() {
    }

    public static PetProfileDTO profile(User user) {
        ensureAccountUser(user);
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            ensureAssets(conn, user.getAccountId());
            PetProfileDTO profile = new PetProfileDTO();
            profile.setAccountId(String.valueOf(user.getAccountId()));
            profile.setAssets(loadAssets(conn, user.getAccountId()));
            profile.setDogs(loadDogs(conn, user.getAccountId()));
            profile.setCheckinStatus(todayCheckinStatus());
            session.commit();
            return profile;
        } catch (Exception e) {
            throw new IllegalStateException("读取狗狗资料失败", e);
        }
    }

    public static PetProfileDTO adopt(User user, PetAdoptDTO adopt) {
        ensureAccountUser(user);
        String breed = normalizeBreed(adopt == null ? null : adopt.getBreed());
        String name = normalizeName(adopt == null ? null : adopt.getName());
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            ensureAssets(conn, user.getAccountId());
            PetProfileDTO.Assets assets = loadAssets(conn, user.getAccountId());
            int dogCount = dogCount(conn, user.getAccountId());
            if (dogCount >= assets.getDogSlots()) {
                throw new IllegalArgumentException("狗位已满");
            }
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO pet_dogs (id, account_id, name, breed, stage, speed, stamina, burst, wisdom, bond, energy, status, race_count, race_first_count, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, 'puppy', ?, ?, ?, ?, ?, ?, 'idle', 0, 0, ?, ?)")) {
                ps.setString(1, "dog-" + UUID.randomUUID());
                ps.setLong(2, user.getAccountId());
                ps.setString(3, name);
                ps.setString(4, breed);
                ps.setInt(5, DEFAULT_STAT);
                ps.setInt(6, DEFAULT_STAT);
                ps.setInt(7, DEFAULT_STAT);
                ps.setInt(8, DEFAULT_STAT);
                ps.setInt(9, DEFAULT_STAT);
                ps.setInt(10, DEFAULT_ENERGY);
                ps.setLong(11, now);
                ps.setLong(12, now);
                ps.executeUpdate();
            }
            session.commit();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("领养狗狗失败", e);
        }
        return profile(user);
    }

    public static PetProfileDTO applyRaceResult(User user, PetRaceResultDTO result) {
        ensureAccountUser(user);
        return applyRaceResult(user.getAccountId(), result);
    }

    public static PetProfileDTO applyRaceResult(long accountId, PetRaceResultDTO result) {
        ensureAccountId(accountId);
        if (result == null || StrUtil.isBlank(result.getDogId())) {
            throw new IllegalArgumentException("赛跑结果缺少狗狗");
        }
        if (result.getRank() < 1) {
            throw new IllegalArgumentException("赛跑名次无效");
        }
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            PetProfileDTO.Dog dog = loadDog(conn, accountId, result.getDogId());
            if (dog == null) {
                throw new IllegalArgumentException("狗狗不存在");
            }
            int raceCount = dog.getRaceCount() + 1;
            int firstCount = dog.getRaceFirstCount() + (result.getRank() == 1 ? 1 : 0);
            int weeklyPoints = dog.getWeeklyPoints() + Math.max(result.getWeeklyPoints(), 0);
            String stage = nextStage(dog, raceCount, firstCount);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE pet_dogs SET race_count=?, race_first_count=?, weekly_points=?, stage=?, updated_at=? WHERE id=? AND account_id=?")) {
                ps.setInt(1, raceCount);
                ps.setInt(2, firstCount);
                ps.setInt(3, weeklyPoints);
                ps.setString(4, stage);
                ps.setLong(5, System.currentTimeMillis());
                ps.setString(6, result.getDogId());
                ps.setLong(7, accountId);
                ps.executeUpdate();
            }
            session.commit();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("更新赛跑结果失败", e);
        }
        return profile(accountId);
    }

    public static PetProfileDTO changeBones(long accountId, int delta) {
        ensureAccountId(accountId);
        if (delta == 0) {
            return profile(accountId);
        }
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            ensureAssets(conn, accountId);
            PetProfileDTO.Assets assets = loadAssets(conn, accountId);
            int nextBones = assets.getBones() + delta;
            if (nextBones < 0) {
                throw new IllegalArgumentException("骨头币不足");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE pet_assets SET bones=?, updated_at=? WHERE account_id=?")) {
                ps.setInt(1, nextBones);
                ps.setLong(2, System.currentTimeMillis());
                ps.setLong(3, accountId);
                ps.executeUpdate();
            }
            session.commit();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("更新骨头币失败", e);
        }
        return profile(accountId);
    }

    public static PetProfileDTO spendRaceSignup(long accountId, String dogId, int energyCost, int bonesCost) {
        ensureAccountId(accountId);
        if (StrUtil.isBlank(dogId)) {
            throw new IllegalArgumentException("赛跑报名缺少狗狗");
        }
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            ensureAssets(conn, accountId);
            PetProfileDTO.Assets assets = loadAssets(conn, accountId);
            PetProfileDTO.Dog dog = loadDog(conn, accountId, dogId);
            if (dog == null) {
                throw new IllegalArgumentException("狗狗不存在");
            }
            if (assets.getBones() < bonesCost) {
                throw new IllegalArgumentException("骨头币不足");
            }
            if (dog.getEnergy() < energyCost) {
                throw new IllegalArgumentException("狗狗活力不足");
            }
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE pet_assets SET bones=?, updated_at=? WHERE account_id=?")) {
                ps.setInt(1, assets.getBones() - bonesCost);
                ps.setLong(2, now);
                ps.setLong(3, accountId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE pet_dogs SET energy=?, updated_at=? WHERE id=? AND account_id=?")) {
                ps.setInt(1, dog.getEnergy() - energyCost);
                ps.setLong(2, now);
                ps.setString(3, dogId);
                ps.setLong(4, accountId);
                ps.executeUpdate();
            }
            session.commit();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("赛跑报名扣费失败", e);
        }
        return profile(accountId);
    }

    public static PetProfileDTO.Dog findRaceDog(long accountId) {
        ensureAccountId(accountId);
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            ensureAssets(conn, accountId);
            List<PetProfileDTO.Dog> dogs = loadDogs(conn, accountId);
            session.commit();
            for (PetProfileDTO.Dog dog : dogs) {
                if ("idle".equals(dog.getStatus()) || dog.getStatus() == null) {
                    return dog;
                }
            }
            return dogs.isEmpty() ? null : dogs.get(0);
        } catch (Exception e) {
            throw new IllegalStateException("读取参赛狗狗失败", e);
        }
    }

    private static PetProfileDTO profile(long accountId) {
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            ensureAssets(conn, accountId);
            PetProfileDTO profile = new PetProfileDTO();
            profile.setAccountId(String.valueOf(accountId));
            profile.setAssets(loadAssets(conn, accountId));
            profile.setDogs(loadDogs(conn, accountId));
            profile.setCheckinStatus(todayCheckinStatus());
            session.commit();
            return profile;
        } catch (Exception e) {
            throw new IllegalStateException("读取狗狗资料失败", e);
        }
    }

    private static void ensureAssets(Connection conn, long accountId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT account_id FROM pet_assets WHERE account_id=?")) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO pet_assets (account_id, bones, food, makeup_cards, dog_slots, energy_limit, created_at, updated_at) VALUES (?, 100, 0, 0, 1, 10, ?, ?)")) {
            ps.setLong(1, accountId);
            ps.setLong(2, now);
            ps.setLong(3, now);
            ps.executeUpdate();
        }
    }

    private static PetProfileDTO.Assets loadAssets(Connection conn, long accountId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT bones, food, makeup_cards, dog_slots, energy_limit FROM pet_assets WHERE account_id=?")) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new PetProfileDTO.Assets();
                }
                return new PetProfileDTO.Assets(
                        rs.getInt("bones"),
                        rs.getInt("food"),
                        rs.getInt("makeup_cards"),
                        rs.getInt("dog_slots"),
                        rs.getInt("energy_limit"));
            }
        }
    }

    private static List<PetProfileDTO.Dog> loadDogs(Connection conn, long accountId) throws Exception {
        List<PetProfileDTO.Dog> dogs = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM pet_dogs WHERE account_id=? ORDER BY created_at ASC")) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    dogs.add(toDog(rs));
                }
            }
        }
        return dogs;
    }

    private static PetProfileDTO.Dog loadDog(Connection conn, long accountId, String dogId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM pet_dogs WHERE account_id=? AND id=?")) {
            ps.setLong(1, accountId);
            ps.setString(2, dogId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? toDog(rs) : null;
            }
        }
    }

    private static int dogCount(Connection conn, long accountId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM pet_dogs WHERE account_id=?")) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static PetProfileDTO.Dog toDog(ResultSet rs) throws Exception {
        return new PetProfileDTO.Dog(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("breed"),
                rs.getString("stage"),
                rs.getInt("speed"),
                rs.getInt("stamina"),
                rs.getInt("burst"),
                rs.getInt("wisdom"),
                rs.getInt("bond"),
                rs.getInt("energy"),
                rs.getString("status"),
                rs.getInt("race_count"),
                rs.getInt("race_first_count"),
                rs.getInt("weekly_points"));
    }

    private static PetProfileDTO.CheckinStatus todayCheckinStatus() {
        LocalDate today = LocalDate.now();
        return new PetProfileDTO.CheckinStatus(today.toString(), false, 1, new ArrayList<String>());
    }

    private static String nextStage(PetProfileDTO.Dog dog, int raceCount, int firstCount) {
        if (firstCount >= 10) {
            return "champion";
        }
        int total = dog.getSpeed() + dog.getStamina() + dog.getBurst() + dog.getWisdom() + dog.getBond();
        if (raceCount >= 3 && total >= 150) {
            return "adult";
        }
        return dog.getStage();
    }

    private static String normalizeBreed(String breed) {
        String value = StrUtil.blankToDefault(breed, "native").trim();
        if (!value.matches("shiba|corgi|golden|border_collie|greyhound|poodle|native|husky")) {
            return "native";
        }
        return value;
    }

    private static String normalizeName(String name) {
        String value = StrUtil.blankToDefault(name, "狗狗").trim();
        return value.length() > 12 ? value.substring(0, 12) : value;
    }

    private static void ensureAccountUser(User user) {
        if (user == null || user.isGuest() || user.getAccountId() <= 0L) {
            throw new IllegalArgumentException("请先登录账号");
        }
    }

    private static void ensureAccountId(long accountId) {
        if (accountId <= 0L) {
            throw new IllegalArgumentException("请先登录账号");
        }
    }
}
