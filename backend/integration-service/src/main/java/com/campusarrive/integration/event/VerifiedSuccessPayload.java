package com.campusarrive.integration.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 事件业务负载类型 — 身份核验通过负载。
 *
 * <p>规格来源：SIM-CA-2026-08 第 5.3 节事件链三。
 * 核验服务发布 student.verified.success 时携带的业务数据。</p>
 */
public class VerifiedSuccessPayload {

    @JsonProperty("studentId")
    private String studentId;

    @JsonProperty("idCard")
    private String idCard;

    @JsonProperty("name")
    private String name;

    @JsonProperty("collegeCode")
    private String collegeCode;

    @JsonProperty("photoUrl")
    private String photoUrl;

    @JsonProperty("issueType")
    private String issueType;

    public VerifiedSuccessPayload() {
    }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCollegeCode() { return collegeCode; }
    public void setCollegeCode(String collegeCode) { this.collegeCode = collegeCode; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }
}
