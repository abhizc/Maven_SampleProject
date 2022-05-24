package com.sdet;

import org.testng.Assert;
import org.testng.annotations.Test;

public class AppTest {

    @Test
    public void testlogin1(){
        App myapp = new App();
        Assert.assertEquals(0,myapp.userLogin("abc","abcd"));
    }

    @Test
    public void testlogin2(){
        App myapp = new App();
        Assert.assertEquals(1,myapp.userLogin("abc","123"));
    }

}
