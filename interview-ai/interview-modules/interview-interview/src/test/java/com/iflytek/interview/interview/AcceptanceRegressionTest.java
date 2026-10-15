package com.iflytek.interview.interview;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.security.LoginUser;
import com.iflytek.interview.interview.entity.*;
import com.iflytek.interview.interview.mapper.*;
import com.iflytek.interview.interview.service.*;
import com.iflytek.interview.interview.service.impl.*;
import com.iflytek.interview.interview.controller.InterviewSessionController;
import com.iflytek.interview.interview.dto.InterviewSessionCreateRequest;
import java.util.*;

class AcceptanceRegressionTest {
 private final ObjectMapper json=new ObjectMapper();
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 private void login(long id,String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(id,"demo","s",List.of(role),List.of()),null,List.of()));}
 @Test void terminalStatesCannotRestart(){assertFalse(InterviewStatus.canTransition("completed","ongoing"));assertFalse(InterviewStatus.canTransition("cancelled","completed"));assertTrue(InterviewStatus.canTransition("ongoing","completed"));}
 @Test void stateEndpointChecksOwnershipBeforeReturningSessionId(){
  login(2,"candidate"); var records=mock(InterviewRecordService.class); var states=mock(InterviewStateService.class);
  when(records.getSession(9L,2L)).thenThrow(new BusinessException(403,"forbidden"));
  var c=new InterviewSessionController(records,mock(InterviewSessionEventMapper.class),states);
  assertThrows(BusinessException.class,()->c.state(9L)); verifyNoInteractions(states);
 }
 @Test void anotherCandidateCannotReadRecord(){
  login(2,"candidate"); var mapper=mock(InterviewRecordMapper.class); var service=new InterviewRecordServiceImpl();
  ReflectionTestUtils.setField(service,"baseMapper",mapper); var r=new InterviewRecord();r.setId(9L);r.setUserId(3L);
  when(mapper.selectById(9L)).thenReturn(r);assertThrows(BusinessException.class,()->service.getSession(9L,2L));
 }
 @Test void adminCannotCreateCandidateSession(){
  login(1,"admin");assertThrows(BusinessException.class,()->new InterviewRecordServiceImpl().createSession(1L,new InterviewSessionCreateRequest()));
 }
 private Map<String,Object> report(boolean video,Object question) throws Exception {
  var mapper=mock(InterviewRecordMapper.class); var r=new InterviewRecord();r.setId(8L);r.setAnswerData("original answer");
  when(mapper.selectById(8L)).thenReturn(r);var generator=new ReportGenerator();
  ReflectionTestUtils.setField(generator,"recordMapper",mapper);ReflectionTestUtils.setField(generator,"objectMapper",json);
  Map<String,Object> eval=new HashMap<>();eval.put("overall_score",question);
  generator.generateReport(8L,Map.of("video_available",video,"expression_score",80,"attention_score",70,"ai_report",Map.of("question_evaluation",eval)));
  ArgumentCaptor<UpdateWrapper<InterviewRecord>> cap=ArgumentCaptor.forClass(UpdateWrapper.class);
  verify(mapper).update(isNull(),cap.capture());
  String serialized=cap.getValue().getParamNameValuePairs().values().stream().filter(v->v instanceof String && ((String)v).startsWith("{\"version\"")).map(Object::toString).findFirst().orElseThrow();
  return json.readValue(serialized,Map.class);
 }
 @Test void fusionUsesRealScoresAndRetainsInput() throws Exception {var r=report(true,90);assertEquals(87.0,((Number)r.get("finalScore")).doubleValue());assertEquals("original answer",r.get("input"));assertEquals("已评估",r.get("visionStatus"));}
 @Test void missingVideoDoesNotInventFinalScore() throws Exception {var r=report(false,90);assertNull(r.get("finalScore"));assertEquals("未评估",r.get("visionStatus"));assertEquals(90,((Number)r.get("questionScore")).intValue());}
 @Test void missingTextAndOutOfRangeScoresStayUnevaluated() throws Exception {assertNull(report(true,null).get("finalScore"));assertNull(report(true,999).get("finalScore"));}
}
