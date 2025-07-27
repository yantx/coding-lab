package com.example.server.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Base64;


public class Base64OrRawPasswordEncoder implements PasswordEncoder {

 @Override
 public String encode(CharSequence rawPassword) {
  return rawPassword.toString();
 }

 @Override
 public boolean matches(CharSequence rawPassword, String encodedPassword) {
    // 先明文比对
  if (encodedPassword.equals(rawPassword.toString())) {
   return true;
  }
  // 再尝试 base64 解码比对
  try {
   String decoded = new String(Base64.getDecoder().decode(rawPassword.toString()));
   return encodedPassword.equals(decoded);
  } catch (IllegalArgumentException e) {
   System.out.println(e.getMessage());
   return false;
  }
 }

 public static void main(String[] args) {
  System.out.println(Base64.getEncoder().encodeToString("password123".getBytes()));
 }
// @Override
// public String encode(CharSequence rawPassword) {
//  // 只做明文存储
//  return rawPassword.toString();
// }
//
// @Override
// public boolean matches(CharSequence rawPassword, String encodedPassword) {
//  // 先明文比对
//  if (encodedPassword.equals(rawPassword.toString())) {
//   return true;
//  }
//  // 再尝试 base64 解码比对
//  try {
//   String decoded = new String(Base64.getDecoder().decode(rawPassword.toString()));
//   return encodedPassword.equals(decoded);
//  } catch (IllegalArgumentException e) {
//   return false;
//  }
// }
}