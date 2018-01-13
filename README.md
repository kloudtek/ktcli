# Description
Extends picocli to provide a configuration framework, dynamic subcommands, logging and several other utilities to help create powerful command line tools

# Usage

Using ktcli is easy:

1) Import ktcli in your project.
    - maven
    ```$xml
    <dependency>
        <groupId>com.kloudtek.ktcli</groupId>
        <artifactId>ktcli</artifactId>
        <version>0.9.1</version>
    </dependency>
    ```
2) Annotate your classes with [picocli](https://github.com/remkop/picocli) annotations
3) Create CliHelper and use `initAndRun` command

    for example:
    ```$java
    public void run(String... args) {
        MyCommandObj cmdObj = new MyCommandObj();
        new CliHelper(cmdObj).initAndRun(args);
    }
    ```