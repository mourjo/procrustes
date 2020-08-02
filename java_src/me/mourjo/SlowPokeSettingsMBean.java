package me.mourjo;

public interface SlowPokeSettingsMBean {

  int DEFAULT_REQUEST_TIME_SEC = 3;

  int getSlowPokeTime();

  void setSlowPokeTime(int seconds);
}
