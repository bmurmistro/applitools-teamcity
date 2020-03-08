package com.applitools;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.FileLogger;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.StdoutLogHandler;
import com.applitools.eyes.selenium.Eyes;
import com.applitools.eyes.selenium.fluent.Target;
import com.codeborne.selenide.WebDriverRunner;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WrapsDriver;

public class EyesWatcher
    extends TestWatcher
{
  public static Eyes eyes = new Eyes();

  private String testName;

  private static BatchInfo batch;

  private static final String APPLITOOLS_KEY = System.getProperty("APPLITOOLS_API_KEY", System.getenv("APPLITOOLS_API_KEY"));

  private static final String APPLICATION_NAME = System.getProperty("applicationName", "Applitools Test App");
  
  private static DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

  static {
    String localBranchName = System.getProperty("branchName", System.getenv("teamcity.build.vcs.branch"));
    if (localBranchName == null) {
      localBranchName = "default";
    }
    eyes.setIsDisabled(APPLITOOLS_KEY == null);

    if (!eyes.getIsDisabled()) {
      String buildNumber = System.getenv("build.vcs.number");
      batch = new BatchInfo(localBranchName + (buildNumber != null ? " #" + buildNumber : " " + dateFormat.format(new Date())));

      // Aggregates tests under the same batch when tests are run in different processes (e.g. split tests in bamboo).
      if (buildNumber != null) {
        batch.setId(batch.getName());
      }

      eyes.setApiKey(APPLITOOLS_KEY);
      eyes.setBatch(batch);

      eyes.setBranchName(localBranchName);

      // set the default parent branch to master if the parent branch is not specified
      eyes.setParentBranchName(System.getProperty("parentBranchName", "default"));

      eyes.setIgnoreCaret(true);
    }
    eyes.setLogHandler(new FileLogger("/Users/brandonmurray/dev/applitools/bmurmistro/applitools.log",false,true));
  }

  @Override
  protected void starting(Description description) {
    if (!eyes.getIsDisabled() && eyes.getBatch() == null) {
      throw new IllegalArgumentException(
          "The branchName parameter or the Bamboo environment variables are required if visual testing is enabled " +
              "(the applitoolsKey property is provided).");
    }
    testName = description.getTestClass().getSimpleName() + "." + description.getMethodName();
  }

  @Override
  protected void finished(Description description) {
    try {
      // End visual testing. Validate visual correctness.
      if (eyes.getIsOpen()) {
        eyes.close(true);
      }
    }
    finally {
      testName = null;
      // Abort test in case of an unexpected error.
      eyes.abortIfNotClosed();
    }
  }

  public void eyesCheck() {
    eyesCheck(null);
  }

  /**
   * Convenience method for performing the Applitools validation.
   *
   * @param tag or step name of the validation
   */
  public void eyesCheck(String tag) {
    if (!eyes.getIsOpen()) {
      WebDriver remoteDriver = WebDriverRunner.getAndCheckWebDriver();

      if (remoteDriver instanceof WrapsDriver) {
        remoteDriver = ((WrapsDriver) remoteDriver).getWrappedDriver();
      }
      
      eyes.open(remoteDriver, APPLICATION_NAME, testName, new RectangleSize(800, 600));
    }
    eyes.check(tag, Target.window());
  }
}