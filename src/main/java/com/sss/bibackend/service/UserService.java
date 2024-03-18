package com.sss.bibackend.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.sss.bibackend.model.entity.User;
import com.sss.bibackend.model.vo.user.LoginUserVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author lenovo
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2024-03-17 18:52:24
*/
public interface UserService extends IService<User> {

    long userRegister(String userAccount, String userPassword, String checkPassword);

    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    LoginUserVO getLoginUserVO(User user);

    User getLoginUser(HttpServletRequest request);

    boolean userLogout(HttpServletRequest request);
}
