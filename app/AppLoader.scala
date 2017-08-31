import play.api.{ LoggerConfigurator, Application, ApplicationLoader }
import components.AppComponents

import play.api.ApplicationLoader.Context

class AppLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach { _.configure(context.environment) }

    println(
      """

                     .-""--.
                   _/-=-.   \
                  (_|a a/   |_
                   / "  \   ,_)
              _    \`=' /__/
             / \_  .;--'  `-.
             \___)//      ,  \
              \ \/;        \  \
               \_.|         | |
                .-\ '     _/_/
              .'  _;.    (_  \
             /  .'   `\   \\_/
            |_ /       |  |\\
           /  _)       /  / ||
      jgs /  /       _/  /  //
          \_/       ( `-/  ||
                    /  /   \\ .-.
                    \_/     \'-'/
  Welcome to Bonobo          `"` 

""")

    val components = new AppComponents(context)
    components.application
  }
}
