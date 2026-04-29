package com.kkiri.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/auth")
public class AuthViewController {
	
	@GetMapping("/login")
	public String login() {
	    return "auth/login";
	}
	
	@GetMapping("/signup")
	public String signup() {
	    return "auth/sign_up";
	}
	
	@GetMapping("/signupuser")
	public String signupuser() {
		return "auth/sign_up-user";
	}
	
}
