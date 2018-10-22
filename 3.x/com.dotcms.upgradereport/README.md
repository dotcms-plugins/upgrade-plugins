
# README
This is an example of how to create and load Jersey Based REST resources in dotCMS via OSGi and Annotation Framework based on AOP 

On the build.gradle you can see the necessary configuration to apply our aspects on your plugin in order to be able to use 
the annotations in your project; currently dotCMS is using aspectjs as an AOP provider and compile-time injection for aspects.

In this example you can see how to use three annotations:

#### LogTime
Any method annotated with this annotation will log (if the appender is enable) the time needed to execute the method.

#### CloseDBIfOpened
This annotation will close open connections in the thread local, in addition to any hibernate session, fully recommended to use on API or Services that calls persistence components.

#### WrapInTransaction
This annotation will handle a local transaction for you (if needed, it can handle nested transaction too).
It will provide an appropiate connection, if needed and handle the commit, rollback or close resources depending on the execution of the method.

## How to build this example

To install all you need to do is build the JAR. to do this run
`./gradlew jar`

This will build two jars in the `build/libs` directory: a bundle fragment (in order to expose needed 3rd party libraries from dotCMS) and the plugin jar 

* **To install this bundle:**

    Copy the bundle jar files inside the Felix OSGI container (*dotCMS/felix/load*).
        
    OR
        
    Upload the bundle jars files using the dotCMS UI (*CMS Admin->Dynamic Plugins->Upload Plugin*).

* **To uninstall this bundle:**
    
    Remove the bundle jars files from the Felix OSGI container (*dotCMS/felix/load*).

    OR

    Undeploy the bundle jars using the dotCMS UI (*CMS Admin->Dynamic Plugins->Undeploy*).

## How to test

Once installed, you can access by get this resource by (this assumes you are on localhost)

`http://localhost:8080/api/v1/custom/content/inode/006de26b-376c-495a-9f7d-913a578b033d`

or this by delete, which requires an dotcms user to access(See authentication below)

**Note: keep in mind this resources will delete last month content!**

`http://localhost:8080/api/v1/custom/content/lastMonth`

## Authentication

This API supports the same REST auth infrastructure as other 
rest apis in dotcms. There are 4 ways to authenticate.

* user/xxx/password/yyy in the URI
* basic http/https authentication (base64 encoded)
* DOTAUTH header similar to basic auth and base64 encoded, e.g. setHeader("DOTAUTH", base64.encode("admin@dotcms.com:admin"))
* Session based (form based login) for frontend or backend logged in user
* JWT