package com.common.returns;

import lombok.Data;

@Data
public class ResRow {
    private Boolean res=false;
    private String data;
    public  ResRow(){
    }
    public  ResRow(String data){
        this.data=data;
        this.res=false;
    }

    public  ResRow(String data, boolean res){
        this.data=data;
        this.res=res;
    }

    public void setData(String data) {
        this.data=data;
    }

    public String getData() {
        return data;
    }

}