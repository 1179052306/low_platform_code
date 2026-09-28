package com.common.returns;

public class ResMsg {

  private Boolean res = true;
  private String code = "200";
  private String msg = "方法执行成功";

  private int errtype = 0;

  public int getErrtype() {
    return errtype;
  }

  public void setErrtype(int errtype) {
    this.errtype = errtype;
  }

  private Object data;

  private Object customValue;

  public Object getData() {
    return data;
  }

  public void setData(Object data) {
    this.data = data;
  }

  public ResMsg() {
  }

  public ResMsg(Boolean res, String data) {
    this.data = data;
    this.res = res;
  }

  public Object getCustomValue() {
    return customValue;
  }

  public void setCustomValue(Object customValue) {
    this.customValue = customValue;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public Boolean getRes() {
    return res;
  }

  public void setRes(Boolean res) {
    this.res = res;
  }

  public String getMsg() {
    return msg;
  }

  public void setMsg(String msg) {
    this.msg = msg;
  }
}