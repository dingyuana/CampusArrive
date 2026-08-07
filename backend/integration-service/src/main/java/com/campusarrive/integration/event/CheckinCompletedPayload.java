package com.campusarrive.integration.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 事件业务负载类型 — 报到全流程完成负载。
 *
 * <p>规格来源：SIM-CA-2026-08 第 5.3 节事件链四。
 * 报到服务发布 student.checkin.completed 时携带的业务数据。
 * 此事件由流程服务聚合判定后触发，确保前置环节齐全。</p>
 */
public class CheckinCompletedPayload {

    @JsonProperty("studentId")
    private String studentId;

    @JsonProperty("idCard")
    private String idCard;

    @JsonProperty("name")
    private String name;

    @JsonProperty("collegeCode")
    private String collegeCode;

    @JsonProperty("classNo")
    private String classNo;

    @JsonProperty("counselorId")
    private String counselorId;

    @JsonProperty("completedTime")
    private String completedTime;

    public CheckinCompletedPayload() {
    }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCollegeCode() { return collegeCode; }
    public void setCollegeCode(String collegeCode) { this.collegeCode = collegeCode; }
    public String getClassNo() { return classNo; }
    public void setClassNo(String classNo) { this.classNo = classNo; }
    public String getCounselorId() { return counselorId; }
    public void setCounselorId(String counselorId) { this.counselorId = counselorId; }
    public String getCompletedTime() { return completedTime; }
    public void setCompletedTime(String completedTime) { this.completedTime = completedTime; }
}
