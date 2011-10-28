package com.goldin.plugins.about

import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.gcommons.beans.ExecOption
import com.goldin.plugins.common.BaseGroovyMojo
import java.text.SimpleDateFormat
import org.apache.maven.plugin.MojoExecutionException
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase

/**
 * Updates files specified with "about" build metadata
 */
@MojoGoal( 'create-about' )
@MojoPhase( 'package' )
@SuppressWarnings( [ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ] )
class AboutMojo extends BaseGroovyMojo
{
    public static final String SEPARATOR = '|==============================================================================='

    @MojoParameter
    public String  prefix = 'META-INF'

    @MojoParameter ( defaultValue = 'about-${project.groupId}-${project.artifactId}-${project.version}.txt' )
    public String  fileName

    @MojoParameter
    public boolean updateArchives = true

    @MojoParameter
    public  String addContent = ''
    private String evaluateAddContent()
    {
        addContent.trim().with { ( startsWith( '{{' ) && endsWith( '}}' )) ? eval(( String ) delegate, String ) : delegate }
    }

    @MojoParameter
    public boolean failOnError = true

    @MojoParameter
    public boolean failIfNotFound = true

    @MojoParameter
    public boolean dumpSCM    = true

    @MojoParameter
    public boolean dumpEnv    = false

    @MojoParameter
    public boolean dumpSystem = false

    @MojoParameter
    public boolean dumpMaven  = false

    @MojoParameter
    public boolean dumpPaths  = false

    @MojoParameter
    public boolean dumpDependencies = false

    @MojoParameter
    public String  endOfLine   = 'windows'

    @MojoParameter ( defaultValue = '${project.build.directory}' )
    public File    directory

    @MojoParameter
    public String  include = '*.jar'

    @MojoParameter
    public String  exclude

    private env       = System.getenv()
    private isWindows = System.getProperty( 'os.name' ).toLowerCase().contains( 'windows' )


    @Requires({ ( s != null ) && prefix })
    @Ensures({ result != null })
    private String padLines ( String s, String prefix )
    {
        List<String> lines = s.readLines()

        ( lines ? ( lines[ 0 ] + (( lines.size() > 1 ) ? '\n' + lines[ 1 .. -1 ].collect { '|' + ( ' ' * prefix.size()) + it }.join( '\n' ) :
                                                         '' )) :
                  '' )
    }


    private String exec ( String command, File directory = basedir, boolean failOnError = true )
    {
        general().executeWithResult( command,
                                     ( isWindows ? ExecOption.CommonsExec : ExecOption.Runtime ),
                                     failOnError,
                                     -1,
                                     directory )
    }


    private String find ( String prefix, String command ) { find( prefix, exec( command ).readLines()) }
    private String find ( String prefix, List<String> l ) { l.find{ it.startsWith( prefix ) }?.replace( prefix, '' )?.trim() ?: '' }
    private String sort ( Map<?,?> map )
    {
        def maxKey = maxKeyLength( map ) + 3
        map.sort().collect { key, value -> " [$key]".padRight( maxKey ) + ":[$value]" }.
                   join( '\n' )
    }


    /**
     * Retrieves result of running "mvn dependency:tree" for the current project.
     *
     * @return Result of running "mvn dependency:tree" for the current project.
     */
    private String dependencyTree()
    {
        String mvnHome = env[ 'M2_HOME' ]
        assert mvnHome, "'M2_HOME' environment variable is not defined"

        File mvnHomeFile = new File( mvnHome )
        mvnHomeFile.with{ assert isDirectory() && new File(( File ) delegate, 'bin' ).directory }

        def mvn = new File( mvnHomeFile, 'bin/mvn' + ( isWindows ? '.bat' : '' )).canonicalPath

        exec( "$mvn -e -B -f ${ new File( basedir, 'pom.xml' ).canonicalPath } " +
              'org.apache.maven.plugins:maven-dependency-plugin:2.3:tree',
              basedir ).replace( '[INFO] ', '' ).
                        replaceAll( /(?s)^.+?@.+?---/,              '' ). // Removing Maven 3 header
                        replaceAll( /(?s)^.+\[dependency:tree.+?]/, '' ). // Removing Maven 2 header
                        replaceAll( /(?m)Downloading: .+$/,         '' ). // Removing Maven 3 download progress indicator
                        replaceAll( /(?m)Downloaded: .+$/,          '' ). // Removing Maven 3 download progress indicator
                        replaceAll( /(?s)----+.+$/,                 '' ). // Removing footer
                        trim()
    }


    String jenkinsContent()
    {
        // https://wiki.jenkins-ci.org/display/JENKINS/Building+a+software+project

        """
        $SEPARATOR
        | Jenkins Info
        $SEPARATOR
        | Server         : [${ env[ 'JENKINS_URL' ] }]
        | Job            : [${ env[ 'JENKINS_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/]
        | Log            : [${ env[ 'JENKINS_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/console]"""
    }


    String hudsonContent()
    {
        // http://weblogs.java.net/blog/johnsmart/archive/2008/03/using_hudson_en.html

        """
        $SEPARATOR
        | Hudson Info
        $SEPARATOR
        | Server         : [${ env[ 'HUDSON_URL' ] }]
        | Job            : [${ env[ 'HUDSON_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/]
        | Log            : [${ env[ 'HUDSON_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/console]"""
    }


    String teamcityContent()
    {
        // http://confluence.jetbrains.net/display/TCD65/Predefined+Build+Parameters

        """
        $SEPARATOR
        | TeamCity Info
        $SEPARATOR
        | Project Name   : [${ env[ 'TEAMCITY_PROJECT_NAME' ] }]
        | Build Config   : [${ env[ 'TEAMCITY_BUILDCONF_NAME' ] }]
        | Build Number   : [${ env[ 'BUILD_NUMBER' ] }]"""
    }


    String serverContent()
    {
        ( env[ 'JENKINS_URL'      ] ? jenkinsContent () :
          env[ 'HUDSON_URL'       ] ? hudsonContent  () :
          env[ 'TEAMCITY_VERSION' ] ? teamcityContent() :
                                      '' )
    }


    @SuppressWarnings( 'LineLength' )
    String buildContent ()
    {
        def props  = System.properties
        def format = new SimpleDateFormat( "dd MMM, EEEE, yyyy, HH:mm:ss (zzzzzz:'GMT'ZZZZZZ)", Locale.ENGLISH )

        """
        $SEPARATOR
        | Build Info
        $SEPARATOR
        | Host           : [${ env[ 'COMPUTERNAME' ] ?: env[ 'HOSTNAME' ] ?: exec( 'hostname' ) ?: '' }]
        | Build Time     : Started         - [${ format.format( session.startTime ) }]
        | Build Time     : "About" created - [${ format.format( new Date())         }]
        | User           : [${ props[ 'user.name' ] }]
        | ${ dumpPaths ? 'Directory      : [' + props[ 'user.dir' ] + ']': '' }
        | Java           : [${ props[ 'java.version' ] }][${ props[ 'java.vm.vendor' ] }]${ dumpPaths ? '[' + props[ 'java.home' ] + ']' : '' }[${ props[ 'java.vm.name' ] }]
        | OS             : [${ props[ 'os.name' ] }][${ props[ 'os.arch' ] }][${ props[ 'os.version' ] }]
        $SEPARATOR
        | Maven Info
        $SEPARATOR
        | ${ dumpPaths ? 'M2_HOME        : [' + env[ 'M2_HOME' ] + ']' : '' }
        | MAVEN_OPTS     : [${ env[ 'MAVEN_OPTS' ] ?: '' }]
        | Version        : [${ mavenVersion() }]
        | Project        : [${ dumpPaths ? project.toString() : project.toString().replaceAll( /\s+@.+/, '' )}]
        | Goals          : $session.goals
        | ${ dumpPaths ? 'Basedir        : [' + basedir.canonicalPath + ']': '' }
        | Name           : ${ ( project.name.startsWith( '[' ) ? '' : '[' ) + project.name + ( project.name.endsWith( ']' ) ? '' : ']' ) }
        | Coordinates    : [$project.groupId:$project.artifactId:$project.version]
        | ${ dumpDependencies ? 'Dependencies   : [' + padLines( dependencyTree(), ' Dependencies   : [' ) + ']' : '' }"""
    }


    String optionalContent()
    {
        def props = System.properties

        section( dumpMaven,                    'Maven Properties'      ) { sort( project.properties ) } +
        section( addContent.trim() as boolean, 'User Content'          ) { evaluateAddContent() }       +
        section( dumpSystem,                   'System Properties'     ) { sort( props ) }              +
        section( dumpEnv,                      'Environment Variables' ) { sort( env   ) }
    }


    @Requires({ title && content })
    @Ensures({ result })
    String section ( boolean condition, String title, Closure content )
    {
        ( condition ?

        """
        $SEPARATOR
        | $title
        $SEPARATOR
        |${ content() }""" :

        '' )
    }


    String scmContent()
    {
        if ( ! dumpSCM ) { return '' }

        File   svnDir     = new File( basedir, '.svn' )
        String svnVersion = null
        String svnStatus  = null
        String gitVersion = null
        String gitStatus  = null

        /**
         * Trying SVN
         */

        if ( svnDir.directory )
        {
            svnVersion = exec( 'svn --version', basedir, false )
            if ( svnVersion.contains( 'svn, version' ))
            {
                svnStatus = exec( "svn status $basedir.canonicalPath", basedir, false )
                if (( ! svnStatus.contains( 'is not a working copy' )) &&
                    ( ! svnStatus.contains( 'containing working copy admin area is missing' )))
                {
                    return svnContent( svnStatus )
                }
            }
        }

        /**
         * Trying Git
         */

        gitVersion = exec( 'git --version', basedir, false )

        if ( gitVersion.contains( 'git version' ))
        {
            gitStatus = exec( 'git status', basedir, false )

            if ( ! gitStatus.contains( 'fatal: Not a git repository' ))
            {
                return gitContent( gitStatus )
            }
        }

        """
        $SEPARATOR
        | SCM Info
        $SEPARATOR
        | Unsupported SCM system: either project is not managed by SVN/Git or corresponding command-line clients are not available.
        | Tried SVN:
        | ~~~~~~~~~~
        | [$svnDir.canonicalPath] - ${ svnDir.directory ? 'exists' : 'does not exist' }
        | ${ svnVersion ? '"svn --version" returned [' + svnVersion + ']'                           : '' }
        | ${ svnStatus  ? '"svn status ' + basedir.canonicalPath + '" returned [' + svnStatus + ']' : '' }
        | Tried Git:
        | ~~~~~~~~~~
        | ${ gitVersion ? '"git --version" returned [' + gitVersion + ']'                            : '' }
        | ${ gitStatus  ? '"git status" returned [' + gitStatus + ']'                                : '' }"""
    }


    String svnContent( String svnStatus )
    {
        /**
         * Path: .
         * URL: http://server/path/project
         * Repository Root: http://server
         * Repository UUID: 3b0d414a-98df-144e-9520-49725f2e85eb
         * Revision: 39134
         * Node Kind: directory
         * Schedule: normal
         * Last Changed Author: Evgeny
         * Last Changed Rev: 39087
         * Last Changed Date: 2011-08-24 09:28:06 +0300 (Wed, 24 Aug 2011)
         */

        List<String> svnInfo = exec( "svn info ${basedir.canonicalPath}" ).readLines()

        /**
         * ------------------------------------------------------------------------
         * r39087 | Evgeny | 2011-08-24 09:28:06 +0300 (Wed, 24 Aug 2011) | 1 line
         *
         * About removed
         * ------------------------------------------------------------------------
         */

        List<String> commitLines = exec( "svn log  ${basedir.canonicalPath} -l 1" ).readLines().grep()

        assert commitLines.size() > 2, \
               "Commit message is too short:\n$commitLines"

        assert [ commitLines[ 0 ], commitLines[ -1 ]].each { it.with { startsWith( '---' ) && endsWith( '---' ) }}, \
               "Unknown commit format:\n$commitLines"

        String       commit        = commitLines[ 1 ]
        List<String> commitMessage = ( commitLines.size() > 3 ) ? commitLines[ 2 .. -2 ]*.trim() : []

        """
        $SEPARATOR
        | SVN Info
        $SEPARATOR
        | Repository     : [${ find( 'URL:',      svnInfo )}]
        | Revision       : [${ find( 'Revision:', svnInfo )}]
        | Status         : [${ padLines( svnStatus, ' Status         : [' ) }]
        | Commit         : [$commit]
        | Commit Date    : [${ split( commit, '\\|' )[ 2 ].trim() }]
        | Commit Author  : [${ split( commit, '\\|' )[ 1 ].trim() }]
        | Commit Message : [${ padLines( commitMessage.join( '\n' ), ' Commit Message : [' ) }]"""
    }


    String gitContent( String gitStatus )
    {
        /**
         * http://schacon.github.com/git/git-log.html
         */
        List<String> log = exec( 'git log -1 --format=format:%h%n%H%n%cD%n%cN%n%ce%n%B' ).readLines()*.trim()

        """
        $SEPARATOR
        | Git Info
        $SEPARATOR
        | Repositories   : [${ padLines( exec( 'git remote -v' ), ' Repositories   : [' ) }]
        | Branch         : [${ find( '# On branch', 'git status' ) }]
        | Git Status     : [${ padLines( gitStatus, ' Git Status     : [' ) }]
        | Commit         : [${ log[ 0 ] }][${ log[ 1 ] }]
        | Commit Date    : [${ log[ 2 ] }]
        | Commit Author  : [${ log[ 3 ] } <${ log[ 4 ] }>]
        | Commit Message : [${ padLines( log[ 5 .. -1 ].join( '\n' ), ' Commit Message : [' ) }]"""
    }


    String allContent()
    {
        ( ' Created with http://evgeny-goldin.com/wiki/Maven-about-plugin\n' +
          serverContent()   +
          scmContent()      +
          buildContent()    +
          optionalContent() + '\n' +
          SEPARATOR ).
        stripMargin().readLines()*.replaceAll( /\s+$/, '' ).grep(). // Deleting empty lines
        join(( 'windows' == endOfLine ) ? '\r\n' : '\n' )
    }


    @Requires({ aboutFile })
    @Ensures({ result == aboutFile })
    File writeAboutFile( File aboutFile )
    {
        file().delete( aboutFile )

        long t = System.currentTimeMillis()

        log.info( "Generating \"about\" in [$aboutFile.canonicalPath], basedir is [${ basedir.canonicalPath }]" )
        file().mkdirs( aboutFile.parentFile )
        aboutFile.write( allContent())
        log.info( "Generated  \"about\" in [$aboutFile.canonicalPath] (${ System.currentTimeMillis() - t } ms)" )

        aboutFile
    }


    @Override
    void doExecute ()
    {
        try
        {
            if ( updateArchives )
            {
                if ( ! directory.directory )
                {
                    assert  ( ! failIfNotFound ), "Directory [$directory.canonicalPath] is not available, consider using <failIfNotFound>"
                    log.warn( "Directory [$directory.canonicalPath] is not available, \"about\" is not created" )
                    return
                }

                def split = { String s -> s ? split( s ) : null }
                def files = file().files( directory, split( include ), split( exclude ), false, false, failIfNotFound )

                if ( files )
                {
                    def aboutFile = new File( outputDirectory(), fileName )
                    def prefix    = (( prefix == '/' ) ? '' : prefix )

                    writeAboutFile( aboutFile )

                    for ( f in files )
                    {
                        def aboutPath = "$f.canonicalPath/$prefix${ prefix ? '/' : '' }$fileName"

                        log.info( "Adding \"about\" to [$aboutPath]" )
                        file().pack( aboutFile.parentFile, f, [ aboutFile.name ], null, false, true, true, null, null, prefix )
                        log.info( "Added  \"about\" to [$aboutPath]" )
                    }

                    file().delete( aboutFile )
                }
                else
                {
                    log.warn( "No files found in [$directory.canonicalPath] and include/exclude patterns [${ include ?: '' }]/[${ exclude ?: '' }]" )
                }
            }
            else
            {
                def aboutFile = ( File ) new File( fileName ).with{ absolute ? delegate : new File( outputDirectory, fileName )}
                writeAboutFile( aboutFile )
            }
        }
        catch ( e )
        {
            def message = 'Failed to create "about" file'
            if ( failOnError ) { throw new MojoExecutionException( message, e ) }
            log.error( message + ':', e )
        }
    }
}

