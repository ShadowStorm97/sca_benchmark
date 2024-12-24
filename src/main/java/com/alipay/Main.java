package com.alipay;

import cloud.agileframework.spring.util.ParamUtil;

import java.util.HashMap;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");
        ParamUtil.getInParamOfArray(new HashMap<>(), "", Object.class);
    }
}