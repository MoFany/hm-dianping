package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * @author MoFany-J
 * @date 2023/3/6
 * @description LoginInterceptor 登录拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 前置拦截器
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 判断是否需要拦截（ThreadLocal中是否有用户）
        if (UserHolder.getUser()==null){
            // 没有，则需要拦截
            response.setStatus(401);
        }
        // 有用户，则放行
        return true;
    }
}
