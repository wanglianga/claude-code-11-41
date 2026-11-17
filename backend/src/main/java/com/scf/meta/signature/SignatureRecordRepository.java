package com.scf.meta.signature;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignatureRecordRepository extends JpaRepository<SignatureRecord, String> {
    List<SignatureRecord> findBySubjectHash(String subjectHash);
    List<SignatureRecord> findByStatus(String status);
}
