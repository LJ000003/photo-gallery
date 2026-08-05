package com.hape.photogallery.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hape.photogallery.entity.ShareToken;

public interface ShareTokenRepository extends JpaRepository<ShareToken, Long> {

    Optional<ShareToken> findByToken(String token);

    /** P0-#6：幂等复用查询——全部活跃 token 由服务层内存比较 photoIds+permission（数据量小） */
    List<ShareToken> findAllByRevokedAtIsNullAndExpiresAtAfter(LocalDateTime now);

    /** 定时清理过期记录 */
    void deleteByExpiresAtBefore(LocalDateTime threshold);
}
