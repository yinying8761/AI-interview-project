package com.iflytek.interview.file.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.file.entity.FileRecord;
import com.iflytek.interview.file.mapper.FileRecordMapper;
import com.iflytek.interview.file.service.FileRecordService;
import org.springframework.stereotype.Service;

@Service
public class FileRecordServiceImpl extends ServiceImpl<FileRecordMapper, FileRecord> implements FileRecordService {
}
