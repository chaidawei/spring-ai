package com.david.springai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 图书实体类，对应数据库 dss_book 表
 */
@Entity
@Table(name = "dss_book")
public class DssBook {

    @Id
    private Long id;

    /** 书名 */
    @Column(name = "title")
    private String title;

    /** 作者 */
    @Column(name = "creator")
    private String creator;

    /** 出版机构 */
    @Column(name = "creator_organ")
    private String creatorOrgan;

    /** 出版日期 */
    @Column(name = "date_publish")
    private String datePublish;

    /** 出版年份 */
    @Column(name = "date_publish_year")
    private String datePublishYear;

    /** 内容描述 */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 出版社 */
    @Column(name = "publisher")
    private String publisher;

    /** 语种 */
    @Column(name = "language")
    private String language;

    /** ISBN */
    @Column(name = "identifier_isbn")
    private String identifierIsbn;

    /** 电子ISBN */
    @Column(name = "identifier_eisbn")
    private String identifierEisbn;

    /** 封面URL */
    @Column(name = "cover_url")
    private String coverUrl;

    /** 关键词 */
    @Column(name = "subject_keyword")
    private String subjectKeyword;

    /** 中图分类 */
    @Column(name = "subject_cnlib")
    private String subjectCnlib;

    /** 教育分类 */
    @Column(name = "subject_cnedu")
    private String subjectCnedu;

    /** 媒介格式 */
    @Column(name = "format_medium")
    private String formatMedium;

    /** 标识符 */
    @Column(name = "identifier")
    private String identifier;

    // ==================== Getter / Setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public String getCreatorOrgan() { return creatorOrgan; }
    public void setCreatorOrgan(String creatorOrgan) { this.creatorOrgan = creatorOrgan; }

    public String getDatePublish() { return datePublish; }
    public void setDatePublish(String datePublish) { this.datePublish = datePublish; }

    public String getDatePublishYear() { return datePublishYear; }
    public void setDatePublishYear(String datePublishYear) { this.datePublishYear = datePublishYear; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getIdentifierIsbn() { return identifierIsbn; }
    public void setIdentifierIsbn(String identifierIsbn) { this.identifierIsbn = identifierIsbn; }

    public String getIdentifierEisbn() { return identifierEisbn; }
    public void setIdentifierEisbn(String identifierEisbn) { this.identifierEisbn = identifierEisbn; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getSubjectKeyword() { return subjectKeyword; }
    public void setSubjectKeyword(String subjectKeyword) { this.subjectKeyword = subjectKeyword; }

    public String getSubjectCnlib() { return subjectCnlib; }
    public void setSubjectCnlib(String subjectCnlib) { this.subjectCnlib = subjectCnlib; }

    public String getSubjectCnedu() { return subjectCnedu; }
    public void setSubjectCnedu(String subjectCnedu) { this.subjectCnedu = subjectCnedu; }

    public String getFormatMedium() { return formatMedium; }
    public void setFormatMedium(String formatMedium) { this.formatMedium = formatMedium; }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
}
