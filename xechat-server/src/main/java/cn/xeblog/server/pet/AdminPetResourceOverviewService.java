package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.react.request.AdminReact;
import cn.xeblog.commons.entity.react.result.AdminPetResourceOverviewDTO;
import cn.xeblog.commons.entity.react.result.AdminReactResult;
import cn.xeblog.server.account.DbInitializer;
import org.apache.ibatis.session.SqlSession;

import java.util.List;

/**
 * 管理员查看用户狗狗之家资源概览。
 */
public final class AdminPetResourceOverviewService {

    private AdminPetResourceOverviewService() {
    }

    public static AdminReactResult query(AdminReact body) {
        int page = body.getPage() == null || body.getPage() < 1 ? 1 : body.getPage();
        int pageSize = body.getPageSize() == null || body.getPageSize() < 1 ? 20 : body.getPageSize();
        pageSize = Math.min(pageSize, 100);
        int offset = (page - 1) * pageSize;

        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            AdminPetResourceOverviewMapper mapper = session.getMapper(AdminPetResourceOverviewMapper.class);
            String account = trimToNull(body.getAccount());
            String nickname = trimToNull(body.getNickname());
            long total = mapper.count(account, nickname);
            List<AdminPetResourceOverviewDTO> records = mapper.query(account, nickname, offset, pageSize);
            return new AdminReactResult(records, total, page, pageSize);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
