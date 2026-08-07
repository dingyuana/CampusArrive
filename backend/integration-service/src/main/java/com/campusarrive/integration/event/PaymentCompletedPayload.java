package com.campusarrive.integration.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 事件业务负载类型 — 缴费完成负载。
 *
 * <p>规格来源：SIM-CA-2026-08 第 5.3 节事件链二、第 7.3 节财务系统对接。
 * 缴费服务发布 student.payment.completed 时携带的业务数据。</p>
 */
public class PaymentCompletedPayload {

    @JsonProperty("studentId")
    private String studentId;

    @JsonProperty("idCard")
    private String idCard;

    @JsonProperty("name")
    private String name;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("collegeCode")
    private String collegeCode;

    @JsonProperty("dormBuilding")
    private String dormBuilding;

    @JsonProperty("roomNo")
    private String roomNo;

    @JsonProperty("bedNo")
    private String bedNo;

    @JsonProperty("payOrderNo")
    private String payOrderNo;

    @JsonProperty("payAmount")
    private double payAmount;

    @JsonProperty("payMethod")
    private String payMethod;

    public PaymentCompletedPayload() {
    }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getCollegeCode() { return collegeCode; }
    public void setCollegeCode(String collegeCode) { this.collegeCode = collegeCode; }
    public String getDormBuilding() { return dormBuilding; }
    public void setDormBuilding(String dormBuilding) { this.dormBuilding = dormBuilding; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public String getBedNo() { return bedNo; }
    public void setBedNo(String bedNo) { this.bedNo = bedNo; }
    public String getPayOrderNo() { return payOrderNo; }
    public void setPayOrderNo(String payOrderNo) { this.payOrderNo = payOrderNo; }
    public double getPayAmount() { return payAmount; }
    public void setPayAmount(double payAmount) { this.payAmount = payAmount; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
}
