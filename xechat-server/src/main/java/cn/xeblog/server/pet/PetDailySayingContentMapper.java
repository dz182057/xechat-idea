package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.pet.AdminListPetDailySayingsDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * pet_saying_content 表 Mapper。
 */
public interface PetDailySayingContentMapper {

    int countAll();

    PetDailySayingContentRecord findById(@Param("contentId") String contentId);

    List<PetDailySayingContentRecord> listPublishable();

    List<PetDailySayingContentRecord> list(@Param("query") AdminListPetDailySayingsDTO query,
                                           @Param("offset") int offset,
                                           @Param("pageSize") int pageSize);

    int countList(@Param("query") AdminListPetDailySayingsDTO query);

    List<String> listCategories();

    String latestContentVersion();

    int upsert(PetDailySayingContentRecord record);

    int softDelete(@Param("contentId") String contentId,
                   @Param("updatedAt") long updatedAt);

}
