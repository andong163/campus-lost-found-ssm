package com.team.cms.support;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionProbeService {
    private final TestRecordMapper mapper;
    public TransactionProbeService(TestRecordMapper mapper) { this.mapper = mapper; }
    @Transactional(rollbackFor = Exception.class)
    public void insertAndFail() throws Exception {
        TestRecord record = new TestRecord(); record.setName("rollback-probe");
        mapper.insert(record);
        throw new Exception("expected checked exception");
    }
    @Transactional
    public void insertNormally() {
        TestRecord record = new TestRecord(); record.setName("commit-probe"); mapper.insert(record);
    }
}
