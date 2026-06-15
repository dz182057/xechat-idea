package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * dogs 表 Mapper。
 */
public interface PetDogMapper {

    void insert(PetDogRecord dog);

    PetDogRecord findByIdAndOwner(@Param("id") String id, @Param("ownerId") long ownerId);

    List<PetDogRecord> listByOwner(@Param("ownerId") long ownerId);

    int countByOwner(@Param("ownerId") long ownerId);

    int updateName(@Param("id") String id,
                   @Param("ownerId") long ownerId,
                   @Param("name") String name,
                   @Param("updatedAt") long updatedAt);

    int updateCareStats(@Param("id") String id,
                        @Param("ownerId") long ownerId,
                        @Param("bond") int bond,
                        @Param("energy") int energy,
                        @Param("updatedAt") long updatedAt);

    int updateStage(@Param("id") String id,
                    @Param("ownerId") long ownerId,
                    @Param("stage") String stage,
                    @Param("updatedAt") long updatedAt);

    int recordRaceResult(@Param("id") String id,
                         @Param("ownerId") long ownerId,
                         @Param("firstPlaceIncrement") int firstPlaceIncrement,
                         @Param("updatedAt") long updatedAt);

    int startExplore(@Param("id") String id,
                     @Param("ownerId") long ownerId,
                     @Param("energyCost") int energyCost,
                     @Param("location") String location,
                     @Param("exploreEndsAt") long exploreEndsAt,
                     @Param("durationHours") int durationHours,
                     @Param("updatedAt") long updatedAt);

    int openExplore(@Param("id") String id,
                    @Param("ownerId") long ownerId,
                    @Param("updatedAt") long updatedAt);

    int resetExplore(@Param("id") String id,
                     @Param("ownerId") long ownerId,
                     @Param("updatedAt") long updatedAt);

    int finishExploreNow(@Param("id") String id,
                         @Param("ownerId") long ownerId,
                         @Param("exploreEndsAt") long exploreEndsAt,
                         @Param("durationHours") int durationHours,
                         @Param("updatedAt") long updatedAt);

    int refreshExpiredEnergy(@Param("ownerId") long ownerId,
                             @Param("energyLimit") int energyLimit,
                             @Param("today") String today,
                             @Param("updatedAt") long updatedAt);

}
