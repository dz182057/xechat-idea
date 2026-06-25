package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗每日问候内容条目。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetDailySayingContentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

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
    private int charCount;
    private double recommendedWeight;
    private String copyrightStatus;
    private String reviewStatus;
    private String riskNotes;
    private boolean active;
    private String contentVersion;
    private long createdAt;
    private long updatedAt;

}
