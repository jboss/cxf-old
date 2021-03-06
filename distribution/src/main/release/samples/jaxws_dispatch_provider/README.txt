JAX-WS Dispatch/Provider Demo 
=============================

The demo demonstrates the use of JAX-WS Dispatch and Provider interface.
The client side Dispatch instance invokes upon an endpoint using a JAX-WS 
Provider implementor. There are three differnt invocations from the client. 
The first uses the SOAPMessage data in MESSAGE mode. The second uses the DOMSource 
data in MESSAGE mode. The third uses the DOMSource in PAYLOAD mode. The three 
different messages are constructed by reading in the XML files found in the 
src/demo/hwDispatch/client directory.

Please review the README in the samples directory before
continuing.


Prerequisite
------------

If your environment already includes cxf-manifest.jar on the
CLASSPATH, and the JDK and ant bin directories on the PATH
it is not necessary to set the environment as described in
the samples directory's README.  If your environment is not
properly configured, or if you are planning on using wsdl2java,
javac, and java to build and run the demos, you must set the
environment.


Building and running the demo using Ant
---------------------------------------

From the base directory of this sample (i.e., where this README file is
located), the Ant build.xml file can be used to build and run the demo. 
The server and client targets automatically build the demo.

Using either UNIX or Windows:

  ant server  (from one command line window)
  ant client  (from a second command line window)
    

To remove the code generated from the WSDL file and the .class
files, run "ant clean".


Building the demo using wsdl2java and javac
-------------------------------------------

From the base directory of this sample (i.e., where this README file is
located) first create the target directory build/classes and then 
generate code from the WSDL file.


For UNIX:
  mkdir -p build/classes

  wsdl2java -d build/classes -compile ./wsdl/hello_world.wsdl

For Windows:
  mkdir build\classes
    Must use back slashes.

  wsdl2java -d build\classes -compile .\wsdl\hello_world.wsdl
    May use either forward or back slashes.

Now compile the provided client and server applications with the commands:

For UNIX:  
  
  export CLASSPATH=$CLASSPATH:$CXF_HOME/lib/cxf-manifest.jar:./build/classes
  javac -d build/classes src/demo/hwDispatch/client/*.java
  javac -d build/classes src/demo/hwDispatch/server/*.java

For Windows:
  set classpath=%classpath%;%CXF_HOME%\lib\cxf-manifest.jar;.\build\classes
  javac -d build\classes src\demo\hwDispatch\client\*.java
  javac -d build\classes src\demo\hwDispatch\server\*.java
  
Finally, copy resource files into the build/classes directory with the commands:

For UNIX:    
  cp ./src/demo/hwDispatch/client/*.xml ./build/classes/demo/hwDispatch/client
  cp ./src/demo/hwDispatch/server/*.xml ./build/classes/demo/hwDispatch/server

For Windows:
  copy src\demo\hwDispatch\client\*.xml build\classes\demo\hwDispatch\client
  copy src\demo\hwDispatch\server\*.xml build\classes\demo\hwDispatch\server


Running the demo using java
---------------------------

From the base directory of this sample (i.e., where this README file is
located) run the commands, entered on a single command line:

For UNIX (must use forward slashes):
    java -Djava.util.logging.config.file=$CXF_HOME/etc/logging.properties
         demo.hwDispatch.server.Server &

    java -Djava.util.logging.config.file=$CXF_HOME/etc/logging.properties
         demo.hwDispatch.client.Client ./wsdl/hello_world.wsdl

The server process starts in the background.  After running the client,
use the kill command to terminate the server process.

For Windows (may use either forward or back slashes):
  start 
    java -Djava.util.logging.config.file=%CXF_HOME%\etc\logging.properties
         demo.hwDispatch.server.Server

    java -Djava.util.logging.config.file=%CXF_HOME%\etc\logging.properties
       demo.hwDispatch.client.Client .\wsdl\hello_world.wsdl

A new command windows opens for the server process.  After running the
client, terminate the server process by issuing Ctrl-C in its command window.

To remove the code generated from the WSDL file and the .class
files, either delete the build directory and its contents or run:

  ant clean
