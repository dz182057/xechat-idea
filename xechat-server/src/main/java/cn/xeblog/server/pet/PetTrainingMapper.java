package cn.xeblog.server.pet;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 训狗手册 Mapper。
 */
public interface PetTrainingMapper {

    List<PetTrainingSkillRecord> listSkillsByAccountId(@Param("accountId") long accountId);

    PetTrainingSkillRecord findSkill(@Param("accountId") long accountId,
                                      @Param("skillId") String skillId);

    int insertSkill(@Param("accountId") long accountId,
                    @Param("skillId") String skillId,
                    @Param("level") int level,
                    @Param("definitionVersion") String definitionVersion,
                    @Param("updatedAt") long updatedAt);

    int updateSkillLevel(@Param("accountId") long accountId,
                         @Param("skillId") String skillId,
                         @Param("level") int level,
                         @Param("definitionVersion") String definitionVersion,
                         @Param("updatedAt") long updatedAt);

    PetTrainingFlagRecord findFlags(@Param("accountId") long accountId);

    void ensureFlags(@Param("accountId") long accountId, @Param("updatedAt") long updatedAt);

    int grantFirstExploreFreeLearn(@Param("accountId") long accountId, @Param("updatedAt") long updatedAt);

    int consumeFirstExploreFreeLearn(@Param("accountId") long accountId, @Param("updatedAt") long updatedAt);

}
