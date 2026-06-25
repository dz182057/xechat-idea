package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * pet_daily_saying_contents 表 Mapper。
 */
public interface PetDailySayingContentMapper {

    int countByFilters(@Param("keyword") String keyword,
                       @Param("category") String category,
                       @Param("reviewStatus") String reviewStatus,
                       @Param("active") Boolean active);

    List<PetDailySayingContentRecord> listByFilters(@Param("keyword") String keyword,
                                                    @Param("category") String category,
                                                    @Param("reviewStatus") String reviewStatus,
                                                    @Param("active") Boolean active,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    PetDailySayingContentRecord findById(@Param("contentId") String contentId);

    int insertIgnore(PetDailySayingContentRecord record);

    int upsert(PetDailySayingContentRecord record);

    int softDelete(@Param("contentId") String contentId,
                   @Param("updatedAt") long updatedAt);

    List<PetDailySayingContentRecord> listPublishableByCategory(
            @Param("category") String category,
            @Param("excludedContentIds") List<String> excludedContentIds,
            @Param("limit") int limit);

    String latestContentVersion();

}
