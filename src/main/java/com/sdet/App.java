package com.sdet;

import java.util.ResourceBundle;

public class App {
    public int userLogin(String userId, String pWd){
        ResourceBundle rb = ResourceBundle.getBundle("config");
        String  userName = rb.getString("username");
        String  passWord = rb.getString("password");

        if (userId.equals(userName) && pWd.equals(passWord))
            return  1;
        else
            return 0;
    }

}
