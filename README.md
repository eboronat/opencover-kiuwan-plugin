# opencover-kiuwan-plugin
An utility to import [OpenCover](https://github.com/OpenCover/opencover) reports into Kiuwan

## install and run
Follow these steps to install and run this plugin in your Kiuwan analysis:
1. copy dist/opencover-kiuwan-plugin-x.y.z.jar in your {KIUWAN_LOCAL_ANALYZER_INSTALLATION_DIR}/lib.custom directory.

2. install dist/ruledef/KIUWAN.RULES.OPENCOVER.Plugin.rule.xml in your Kiuwan account. See more on this at [Installing rule definitions](https://www.kiuwan.com/docs/display/K5/Installing+rule+definitions+created+with+Kiuwan+Rule+Developer)

3. create or edit a model activating this new rule, and assign the model to your kiuwan applications.

4. once the OpenCover report is inside your source directory, run Kiuwan Local Analyzer program:

    > {kiuwan_local_analyzer_installation_dir}\bin\agent.cmd -n {app_name} -s {src_dir}
    
## how does it work?
This is a technology that allows users to import defects/vulnerabilities into Kiuwan from OpenCover report files. If the coverage (**sequenceCoverage**) of a file is between 0% and 50% (default values), a violation is generated and reported by Kiuwan. These thresholds can be modified by editing the rule from Kiuwan, as well as the name of the report that the rule will search (opencover.xml by default).

Note that the first threshold (minimum) should always be 0%. It can be modified in case you want to add the rule several times to 'play' with the priorities according to the coverage. For example: Rule1 (0%-30%) -> High, Rule2 (30%-60%) -> Medium, Rule3 (60%-80%) -> Low.

### rule KIUWAN.RULES.OPENCOVER.Plugin
This Kiuwan plugin is really a Kiuwan native rule that looks for an OpenCover report file (called opencover.xml by default) and generates 'Kiuwan defects' for each 'file whose coverage percentage is between the rule thresholds' reported in that file.

You need to upload and insert this rule dist/ruledef/KIUWAN.RULES.OPENCOVER.Plugin.rule.xml in your Kiuwan model to ensure that OpenCover report is processed.
