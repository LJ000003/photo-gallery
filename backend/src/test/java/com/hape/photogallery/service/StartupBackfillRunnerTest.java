package com.hape.photogallery.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 启动补生成任务：开关控制是否触发 MigrationService.backfillMissingFilesOnStartup */
@ExtendWith(MockitoExtension.class)
class StartupBackfillRunnerTest {

    @Mock private MigrationService migrationService;

    @Test
    void run_enabled_shouldTriggerBackfill() {
        StartupBackfillRunner runner = new StartupBackfillRunner(migrationService, true);

        runner.run(null);

        verify(migrationService).backfillMissingFilesOnStartup();
    }

    @Test
    void run_disabled_shouldNotTriggerBackfill() {
        StartupBackfillRunner runner = new StartupBackfillRunner(migrationService, false);

        runner.run(null);

        verify(migrationService, never()).backfillMissingFilesOnStartup();
    }
}
