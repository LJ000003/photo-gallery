package com.hape.photogallery.repository;

import com.hape.photogallery.entity.ShareToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** P0-#6：分享 token 持久化——findByToken / 活跃查询（撤销与过期过滤）/ 清理条件 */
@DataJpaTest
class ShareTokenRepositoryTest {

    @Autowired
    private ShareTokenRepository repo;

    private ShareToken token(String t, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        ShareToken st = new ShareToken();
        st.setToken(t);
        st.setPhotoIds("[1,2]");
        st.setPermission("view");
        st.setExpiresAt(expiresAt);
        st.setRevokedAt(revokedAt);
        st.setCreatedAt(LocalDateTime.now());
        return st;
    }

    @Test
    void findByToken_shouldReturnStored() {
        repo.save(token("tok-1", LocalDateTime.now().plusDays(1), null));

        assertThat(repo.findByToken("tok-1")).isPresent();
        assertThat(repo.findByToken("missing")).isEmpty();
    }

    @Test
    void findAllActive_shouldExcludeRevokedAndExpired() {
        repo.save(token("active", LocalDateTime.now().plusDays(1), null));
        repo.save(token("revoked", LocalDateTime.now().plusDays(1), LocalDateTime.now()));
        repo.save(token("expired", LocalDateTime.now().minusHours(1), null));

        List<ShareToken> active = repo.findAllByRevokedAtIsNullAndExpiresAtAfter(LocalDateTime.now());

        assertThat(active).extracting(ShareToken::getToken)
                .containsExactly("active");
    }

    @Test
    void deleteByExpiresAtBefore_shouldRemoveOldRecords() {
        repo.save(token("old", LocalDateTime.now().minusDays(8), null));
        repo.save(token("new", LocalDateTime.now().plusDays(1), null));

        repo.deleteByExpiresAtBefore(LocalDateTime.now().minusDays(7));

        assertThat(repo.findByToken("old")).isEmpty();
        assertThat(repo.findByToken("new")).isPresent();
    }
}
