package io.collectra.platform.revocation;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/** Trusted administrative enrollment, never invoked automatically on JWT validation. */
@Service
public class SubjectEnrollmentService {
    private final JdbcClient db;
    public SubjectEnrollmentService(JdbcClient db) { this.db=db; }

    @Transactional
    public Map<String,Object> initialize(String issuer,String subject) {
        if(issuer==null || issuer.isBlank() || subject==null || subject.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Issuer and subject required");
        db.sql("INSERT INTO subject_access_state(issuer,subject_id,state,version) " +
            "VALUES (:iss,:sub,'ACTIVE',1) ON CONFLICT (issuer,subject_id) DO NOTHING")
            .param("iss",issuer).param("sub",subject).update();
        return db.sql("SELECT state,version FROM subject_access_state " +
            "WHERE issuer=:iss AND subject_id=:sub")
            .param("iss",issuer).param("sub",subject)
            .query((rs,n)->Map.<String,Object>of("state",rs.getString(1),"version",rs.getLong(2)))
            .single();
    }
}
