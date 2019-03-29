package com.ldz.api.interceptor;

import com.ldz.sys.model.SysJg;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AppAccessInterceptor  extends HandlerInterceptorAdapter {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 查看请求类型
        String method = request.getMethod();
        if (method.equals("OPTIONS")) {
            // 如果收到的是跨域预请求消息，直接响应，返回true，以便后续跨域请求成功
            return true;
        }
        String userId = request.getHeader("user_id");

        request.setAttribute("orgCode","100");
        request.getSession().setAttribute("userId",userId);
        if (true) return true;

        String apicode = request.getHeader("apicode");


        if (apicode == null)
            apicode = request.getParameter("apicode");

        if (StringUtils.isEmpty(apicode)){
            response.sendError(HttpStatus.NOT_FOUND.value());
            return false;
        }
        //验证来访者IP是否已授权，如果未授权则拒绝访问

        return isAccess(apicode, request);
    }

    private boolean isAccess(String apicode, HttpServletRequest request){
        try{
            //验证来访者IP是否已授权，如果未授权则拒绝访问
            String ip = getClientIp(request);
            //IP进行MD5加密计算得到一个结果
            String md5Val = ip;
            if (md5Val.equals(apicode)){

            }
            //测试用
            SysJg jgxx = new SysJg();
            jgxx.setJgdm("100");
            jgxx.setJgmc("机构名称");
            request.setAttribute("requestJgxx", jgxx);
        }catch(Exception e){
            return false;
        }

        return true;
    }

    private static String getClientIp(HttpServletRequest request) {
        String ip=request.getHeader("x-forwarded-for");
        if(ip==null || ip.length()==0 || "unknown".equalsIgnoreCase(ip)){
            ip=request.getHeader("Proxy-Client-IP");
        }
        if(ip==null || ip.length()==0 || "unknown".equalsIgnoreCase(ip)){
            ip=request.getHeader("WL-Proxy-Client-IP");
        }
        if(ip==null || ip.length()==0 || "unknown".equalsIgnoreCase(ip)){
            ip=request.getHeader("X-Real-IP");
        }
        if(ip==null || ip.length()==0 || "unknown".equalsIgnoreCase(ip)){
            ip=request.getRemoteAddr();
        }


        return ip;
    }
}
