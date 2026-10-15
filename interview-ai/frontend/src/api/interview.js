import request from './request'

// ==================== 场景模块 ====================
export function listScenarios(params) {
  return request.get('/scenarios', { params })
}

export function getScenario(id) {
  return request.get(`/scenarios/${id}`)
}

export function createScenario(data) {
  return request.post('/scenarios', data)
}

export function updateScenario(id, data) {
  return request.put(`/scenarios/${id}`, data)
}

export function deleteScenario(id) {
  return request.delete(`/scenarios/${id}`)
}

// ==================== 题库模块 ====================
export function listQuestions(params) {
  return request.get('/questions', { params })
}

export function pageQuestions(params) {
  return request.get('/questions/page', { params })
}

export function getQuestion(id) {
  return request.get(`/questions/${id}`)
}

export function getQuestionTags(id) {
  return request.get(`/questions/${id}/tags`)
}

export function getRandomQuestions(params) {
  return request.get('/questions/random', { params })
}

export function createQuestion(data) {
  return request.post('/questions', data)
}

export function updateQuestion(id, data) {
  return request.put(`/questions/${id}`, data)
}

export function deleteQuestion(id) {
  return request.delete(`/questions/${id}`)
}

export function importQuestions(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/questions/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function exportQuestions(params) {
  return request.get('/questions/export', { params, responseType: 'blob' })
}

// ==================== 题目标签模块 ====================
export function listQuestionTags(params) {
  return request.get('/question-tags', { params })
}

export function createQuestionTag(data) {
  return request.post('/question-tags', data)
}

export function updateQuestionTag(id, data) {
  return request.put(`/question-tags/${id}`, data)
}

export function disableQuestionTag(id) {
  return request.patch(`/question-tags/${id}/disable`)
}

// ==================== 智能组卷模块 ====================
export function listPapers(params) {
  return request.get('/question-papers', { params })
}

export function generatePaper(data) {
  return request.post('/question-papers/generate', data)
}

export function getPaperDetail(id) {
  return request.get(`/question-papers/${id}`)
}

export function listAvailablePapers(scenarioId) {
  return request.get('/question-papers/available', { params: { scenarioId } })
}

// ==================== 面试记录模块 ====================
export function createRecord(data) {
  return request.post('/records', data)
}

export function getRecordQuestions(id) {
  return request.get(`/records/${id}/questions`)
}

export function startInterview(id) {
  return request.post(`/records/${id}/start`)
}

export function submitInterview(id, data) {
  return request.post(`/records/${id}/submit`, data)
}

export function cancelInterview(id) {
  return request.post(`/records/${id}/cancel`)
}

export function scoreInterview(id, data) {
  return request.put(`/records/${id}/score`, data)
}

export function listRecords() {
  return request.get('/records')
}

export function listMyRecords() {
  return request.get('/records/my')
}

// ==================== 文件上传 ====================
export function uploadFile(formData, module) {
  return request.post(`/files/upload?module=${module}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
// ==================== 面试会话模块（第05讲） ====================
export function listSessions(params) {
  return request.get('/interview-sessions', { params })
}

export function createSession(data) {
  return request.post('/interview-sessions', data)
}

export function getSession(id) {
  return request.get(`/interview-sessions/${id}`)
}

export function listSessionEvents(id) {
  return request.get(`/interview-sessions/${id}/events`)
}

export function scheduleSession(id, data) {
  return request.post(`/interview-sessions/${id}/schedule`, data)
}

export function joinSession(id, role) {
  return request.post(`/interview-sessions/${id}/join`, null, { params: { role } })
}

export function startSession(id) {
  return request.post(`/interview-sessions/${id}/start`)
}

export function completeSession(id, data) {
  return request.post(`/interview-sessions/${id}/complete`, data)
}

export function cancelSession(id, data) {
  return request.post(`/interview-sessions/${id}/cancel`, data)
}
// ==================== 视觉分析模块 ====================
export function getVisionContext(recordId, config) {
  return request.get(`/interview-vision/records/${recordId}/context`, config)
}

export function submitVisionFrame(sessionId, recordId, questionIndex, image, config) {
  const formData = new FormData()
  formData.append('image', image, 'frame.jpg')
  return request.post(`/interview-vision/${sessionId}/frames`, formData, {
    ...config,
    params: { recordId, questionIndex },
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getVisionMetrics(sessionId, config) {
  return request.get(`/interview-vision/${sessionId}/metrics`, config)
}

export function finishVision(sessionId) {
  return request.post(`/interview-vision/${sessionId}/finish`)
}
