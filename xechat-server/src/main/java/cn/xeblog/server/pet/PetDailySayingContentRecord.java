package cn.xeblog.server.pet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pet_daily_saying_contents 表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetDailySayingContentRecord {

    private String contentId;
    private String category;
    private String subtype;
    private String title;
    private String primaryText;
    private String secondaryText;
    private String author;
    private String work;
    private String sourceType;
    private String sourceUrl;
    private String sourceLocator;
    private String sourceOriginal;
    private String language;
    private String translatorEditor;
    private String tags;
    private String tone;
    private double recommendedWeight;
    private String copyrightStatus;
    private String reviewStatus;
    private String riskNotes;
    private boolean active;
    private String contentVersion;
    private long createdAt;
    private long updatedAt;

}
