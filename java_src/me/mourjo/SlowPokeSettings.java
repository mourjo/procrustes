package me.mourjo;

import java.lang.management.ManagementFactory;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

public class SlowPokeSettings implements SlowPokeSettingsMBean {

  private int timeTakenSec = DEFAULT_REQUEST_TIME_SEC;

  public static void startServer(SlowPokeSettings mbean)
      throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, MalformedObjectNameException {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    ObjectName name = new ObjectName("me.mourjo:type=EnvBean");

    mbs.registerMBean(mbean, name);
  }

  @Override
  public int getSlowPokeTime() {
    return timeTakenSec;
  }

  @Override
  public synchronized void setSlowPokeTime(int seconds) {
    timeTakenSec = seconds;
  }
}
