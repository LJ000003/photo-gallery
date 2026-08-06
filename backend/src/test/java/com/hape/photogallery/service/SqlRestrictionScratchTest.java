package com.hape.photogallery.service;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.repository.PhotoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 临时 scratch 测试：验证 @SQLRestriction("deleted_at IS NULL") 对软删实体 DELETE/UPDATE 的行为。跑完即删。 */
@DataJpaTest
class SqlRestrictionScratchTest {

    @Autowired
    private PhotoRepository photoRepo;

    @Test
    @Transactional
    void deleteSoftDeletedEntity() {
        Photo p = new Photo();
        p.setName("x");
        p.setFileName("x.jpg");
        p = photoRepo.saveAndFlush(p);
        p.setDeletedAt(LocalDateTime.now());
        photoRepo.saveAndFlush(p);

        // 模拟 TrashService.permanentlyDelete：对已软删实体调用 repo.delete
        photoRepo.delete(p);
        photoRepo.flush();
        System.out.println("SCRATCH-RESULT: delete soft-deleted entity OK (no exception)");
    }

    @Test
    @Transactional
    void updateSoftDeletedEntity() {
        Photo p = new Photo();
        p.setName("x");
        p.setFileName("x.jpg");
        p = photoRepo.saveAndFlush(p);
        p.setDeletedAt(LocalDateTime.now());
        photoRepo.saveAndFlush(p);

        // 模拟 TrashService.restore：对已软删实体置 deletedAt=null 后 save
        p.setDeletedAt(null);
        photoRepo.saveAndFlush(p);
        System.out.println("SCRATCH-RESULT: update soft-deleted entity OK (no exception)");
        assertThat(photoRepo.findDeletedById(p.getId())).isEmpty();
    }
}
