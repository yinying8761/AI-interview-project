import request from './request'

export function uploadResume(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/resumes/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getCurrentResume() {
  return request.get('/resumes/current')
}

export function listResumeVersions(resumeId) {
  return request.get(`/resumes/${resumeId}/versions`)
}

export function listResumeKeywords(resumeId) {
  return request.get(`/resumes/${resumeId}/keywords`)
}

export function getResumeFileUrl(versionId) {
  return request.get(`/resumes/versions/${versionId}/url`)
}
