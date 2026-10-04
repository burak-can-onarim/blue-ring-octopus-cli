package com.bcoworks.codeanalyzer.config;

import com.bcoworks.codeanalyzer.command.DashboardCommand;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TuiStartupListener implements ApplicationRunner {

    private final DashboardCommand dashboardCommand;

    public TuiStartupListener(DashboardCommand dashboardCommand) {
        this.dashboardCommand = dashboardCommand;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args.getSourceArgs().length == 0) {
            dashboardCommand.startDashboard();
        }
    }
}