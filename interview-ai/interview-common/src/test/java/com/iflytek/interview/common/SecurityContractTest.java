package com.iflytek.interview.common;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import com.iflytek.interview.common.security.*;
import com.iflytek.interview.common.exception.BusinessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
class SecurityContractTest {
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void anonymousCannotReadIdentity(){assertThrows(BusinessException.class,SecurityUtil::getCurrentUserId);}
 @Test void roleComesFromAuthenticatedPrincipal(){
  LoginUser u=new LoginUser(7L,"candidate","session",List.of("candidate"),List.of("record:view"));
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,List.of()));
  assertEquals(7L,SecurityUtil.getCurrentUserId()); assertTrue(SecurityUtil.hasRole("candidate")); assertFalse(SecurityUtil.hasRole("admin"));
 }
}
