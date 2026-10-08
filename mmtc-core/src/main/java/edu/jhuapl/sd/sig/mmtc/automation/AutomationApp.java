package edu.jhuapl.sd.sig.mmtc.automation;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.automation.config.AutomationAppCliConfig;
import edu.jhuapl.sd.sig.mmtc.automation.config.AutomationAppConfig;
import edu.jhuapl.sd.sig.mmtc.automation.job.AutocorrelateAndTrendingJob;
import edu.jhuapl.sd.sig.mmtc.automation.job.AutocorrelateJob;
import edu.jhuapl.sd.sig.mmtc.automation.job.TrendingJob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

public class AutomationApp {
    private static final Logger logger = LogManager.getLogger();

    private final AutomationAppConfig config;

    public AutomationApp(String[] args) throws MmtcException {
        try {
            this.config = new AutomationAppConfig(new AutomationAppCliConfig(args));
        } catch (Exception e) {
            throw new MmtcException("MMTC auto initialization failed.", e);
        }
    }

    public void run() throws SchedulerException {
        logger.info(USER_NOTICE, "AutomationApp initialization starting...");

        final Properties quartzProps = new Properties();
        quartzProps.setProperty("org.quartz.scheduler.instanceName", "MmtcAutoScheduler");
        quartzProps.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        quartzProps.setProperty("org.quartz.threadPool.threadCount", "1");

        final Scheduler scheduler = new StdSchedulerFactory(quartzProps).getScheduler();

        if (config.getAutomationConfig().autocorrelate.isEnabled) {
            logger.info(USER_NOTICE, "Scheduling autocorrelate job");

            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("automationAppConfig", this.config);
            JobDetail job = JobBuilder.newJob(AutocorrelateJob.class)
                            .withIdentity("autocorrelateJob")
                            .usingJobData(jobDataMap)
                            .build();

            Trigger trigger = buildTriggerFor(config.getAutomationConfig().autocorrelate.cronExpression, "autocorrelateTrigger");
            scheduler.scheduleJob(job, trigger);
        }

        if (config.getAutomationConfig().trending.isEnabled) {
            logger.info(USER_NOTICE, "Scheduling trending job");

            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("automationAppConfig", this.config);
            JobDetail job = JobBuilder.newJob(TrendingJob.class)
                    .withIdentity("trendingJob")
                    .usingJobData(jobDataMap)
                    .build();

            Trigger trigger = buildTriggerFor(config.getAutomationConfig().trending.cronExpression, "trendingTrigger");
            scheduler.scheduleJob(job, trigger);
        }

        if (config.getAutomationConfig().autocorrelateAndTrending.isEnabled) {
            logger.info(USER_NOTICE, "Scheduling autocorrelate-and-trending job");

            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("automationAppConfig", this.config);
            JobDetail job = JobBuilder.newJob(AutocorrelateAndTrendingJob.class)
                    .withIdentity("autocorrelateAndTrendingJob")
                    .usingJobData(jobDataMap)
                    .build();

            Trigger trigger = buildTriggerFor(config.getAutomationConfig().autocorrelateAndTrending.cronExpression, "autocorrelateAndTrendingTrigger");
            scheduler.scheduleJob(job, trigger);
        }

        scheduler.start();

        final CountDownLatch shutdownLatch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info(USER_NOTICE, "Received signal; stopping app...");

                // interrupt jobs
                for (JobExecutionContext jobExecutionContext : scheduler.getCurrentlyExecutingJobs()) {
                    logger.info(USER_NOTICE, String.format("Stopping %s (%s)", jobExecutionContext.getJobDetail().getJobClass().getSimpleName(), jobExecutionContext.getJobDetail().getKey()));
                    scheduler.interrupt(jobExecutionContext.getFireInstanceId());
                }

                // wait for jobs to complete
                logger.info(USER_NOTICE, "Shutting scheduler down...");
                scheduler.shutdown(true);
                logger.info(USER_NOTICE, "Scheduler shut down.");
                config.releaseLockFile();

                // manually shut down log4j2, since we disabled the shutdown hook in the bin/mmtc startup script so we could do it cleanly here after the rest of the logic has finished shutting down
                LogManager.shutdown();
            } catch (SchedulerException | MmtcException e) {
                logger.error(e);
            } finally {
                shutdownLatch.countDown();
            }
        }));

        logger.info(USER_NOTICE, "AutomationApp initialization complete.");

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            logger.error(e);
            Thread.currentThread().interrupt();
        }
    }

    private Trigger buildTriggerFor(String quartzCronExpression, String identity) {
        return TriggerBuilder.newTrigger()
                .withIdentity(identity)
                .withSchedule(CronScheduleBuilder.cronSchedule(quartzCronExpression))
                .build();
    }
}
