package cn.xeblog.server.game.drawguess;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.game.drawguess.DrawGuessWordDTO;
import cn.xeblog.server.account.DbInitializer;
import org.apache.ibatis.session.SqlSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 你画我猜服务端词库。
 */
public final class DrawGuessWordService {

    private DrawGuessWordService() {
    }

    public static List<DrawGuessWordDTO> list() {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            List<DrawGuessWord> rows = session.getMapper(DrawGuessWordMapper.class).listAll();
            List<DrawGuessWordDTO> result = new ArrayList<>(rows.size());
            for (DrawGuessWord row : rows) {
                result.add(toDTO(row));
            }
            return result;
        }
    }

    public static List<DrawGuessWordDTO> save(List<DrawGuessWordDTO> words) {
        List<DrawGuessWordDTO> normalized = normalize(words);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("词库不能为空");
        }

        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            DrawGuessWordMapper mapper = session.getMapper(DrawGuessWordMapper.class);
            mapper.deleteAll();
            for (int i = 0; i < normalized.size(); i++) {
                DrawGuessWordDTO dto = normalized.get(i);
                mapper.insert(DrawGuessWord.builder()
                        .word(dto.getWord())
                        .hint(dto.getHint())
                        .sortOrder(i)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            }
            session.commit();
            return normalized;
        }
    }

    public static DrawGuessWordDTO randomOne() {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            DrawGuessWord row = session.getMapper(DrawGuessWordMapper.class).randomOne();
            return row == null ? null : toDTO(row);
        }
    }

    private static DrawGuessWordDTO toDTO(DrawGuessWord row) {
        return new DrawGuessWordDTO(row.getWord(), row.getHint());
    }

    private static List<DrawGuessWordDTO> normalize(List<DrawGuessWordDTO> words) {
        Map<String, DrawGuessWordDTO> map = new LinkedHashMap<>();
        if (words == null) {
            return new ArrayList<>();
        }
        for (DrawGuessWordDTO item : words) {
            if (item == null || StrUtil.isBlank(item.getWord())) {
                continue;
            }
            String word = item.getWord().trim();
            if (map.containsKey(word)) {
                continue;
            }
            String hint = StrUtil.isBlank(item.getHint()) ? null : item.getHint().trim();
            map.put(word, new DrawGuessWordDTO(word, hint));
        }
        return new ArrayList<>(map.values());
    }

}
