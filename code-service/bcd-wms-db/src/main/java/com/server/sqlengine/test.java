package com.server.sqlengine;

import com.server.sqlengine.model.InsertStatement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class test {

    @Autowired
    SqlEngine sqlEngine;
    private  void  test(){

//        InsertStatement stmt=new InsertStatement(new InsertStatement.Builder());
//        sqlEngine.insert(stmt);
    }
}
