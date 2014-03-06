function assert(msg, that) {
    if (!that) throw "Assertion failed: " + msg;
}

function assertEquals(msg, a, b) {
    var aString = JSON.stringify(a);
    var bString = JSON.stringify(b);
    assert(msg, aString === bString);
}

assertEquals("hourlySchedule() works",
             hourlySchedule(),
             {
                 "type": "com.collective.celos.HourlySchedule"
             });

assertEquals("minutelySchedule() works",
             minutelySchedule(),
             {
                 "type": "com.collective.celos.MinutelySchedule"
             });

assertEquals("cronSchedule() works",
             cronSchedule("* * * * * *"),
             {
                 "type": "com.collective.celos.CronSchedule",
                 "properties": {
                     "celos.cron.config": "* * * * * *"
                 }
             });

assertEquals("serialSchedulingStrategy() works",
             serialSchedulingStrategy(),
             {
                 "type": "com.collective.celos.SerialSchedulingStrategy"
             });

assertEquals("alwaysTrigger() works",
             alwaysTrigger(),
             {
                 "type": "com.collective.celos.AlwaysTrigger"
             });

assertEquals("hdfsCheckTrigger() works",
             hdfsCheckTrigger("/foo", "hdfs://example.com"),
             {
                 "type": "com.collective.celos.HDFSCheckTrigger",
                 "properties": {
                     "celos.hdfs.fs": "hdfs://example.com",
                     "celos.hdfs.path": "/foo"
                 }
             });

assertEquals("andTrigger() works for empty arguments",
             andTrigger(),
             {
                 "type": "com.collective.celos.AndTrigger",
                 "properties": {
                     "celos.andTrigger.triggers": []
                 }
             });

assertEquals("andTrigger() works",
             andTrigger(alwaysTrigger(), hdfsCheckTrigger("/foo", "hdfs://example.com")),
             {
                 "type": "com.collective.celos.AndTrigger",
                 "properties": {
                     "celos.andTrigger.triggers": [alwaysTrigger(), hdfsCheckTrigger("/foo", "hdfs://example.com")]
                 }
             });

assertEquals("oozieExternalService() works",
             oozieExternalService({"a": "1", "b": "2"}, "http://example.com/oozie"),
             {
                 "type": "com.collective.celos.OozieExternalService",
                 "properties": {
                     "a": "1",
                     "b": "2",
                     "celos.oozie.url": "http://example.com/oozie"
                 }
             });
