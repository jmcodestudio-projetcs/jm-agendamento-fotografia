package com.jmcodestudio.agendamento.trial;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrialLifecycleWorker {

    private final TrialLifecycleService trialLifecycleService;

    public TrialLifecycleWorker(TrialLifecycleService trialLifecycleService) {
        this.trialLifecycleService = trialLifecycleService;
    }

    @Scheduled(cron = "${TRIAL_LIFECYCLE_CRON:0 0 3 * * *}")
    public void runDailyCheck() {
        trialLifecycleService.checkTrialLifecycle();
    }
}
