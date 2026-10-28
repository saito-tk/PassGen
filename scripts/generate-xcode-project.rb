#!/usr/bin/env ruby
require 'xcodeproj'

root = File.expand_path('../iosApp', __dir__)
project = Xcodeproj::Project.new(File.join(root, 'Passgen.xcodeproj'))
project.root_object.attributes['LastUpgradeCheck'] = '2700'
project.root_object.attributes['LastSwiftUpdateCheck'] = '2700'
project.root_object.development_region = 'ja'
project.root_object.known_regions = ['ja', 'en', 'Base']

app = project.new_target(:application, 'Passgen', :ios, '18.0')
tests = project.new_target(:unit_test_bundle, 'PassgenTests', :ios, '18.0')
ui_tests = project.new_target(:ui_test_bundle, 'PassgenUITests', :ios, '18.0')
tests.add_dependency(app)
ui_tests.add_dependency(app)

[[app, 'Passgen'], [tests, 'PassgenTests'], [ui_tests, 'PassgenUITests']].each do |target, folder|
  group = project.main_group.new_group(folder, folder)
  Dir.glob(File.join(root, folder, '*.swift')).sort.each do |path|
    target.source_build_phase.add_file_reference(group.new_file(File.basename(path)))
  end
  target.build_configurations.each do |config|
    config.build_settings.merge!({
      'SWIFT_VERSION' => '6.0',
      'SWIFT_STRICT_CONCURRENCY' => 'complete',
      'IPHONEOS_DEPLOYMENT_TARGET' => '18.0',
      'TARGETED_DEVICE_FAMILY' => '1,2',
      'CODE_SIGN_STYLE' => 'Automatic',
      'GENERATE_INFOPLIST_FILE' => 'YES',
      'PRODUCT_BUNDLE_IDENTIFIER' => "jp.passgen.#{folder.downcase}",
      'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO',
      'CLANG_ENABLE_MODULES' => 'YES',
      'MARKETING_VERSION' => '1.0.0',
      'CURRENT_PROJECT_VERSION' => '1',
      'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'x86_64',
      'SWIFT_EMIT_LOC_STRINGS' => 'YES',
    })
  end
end

app_group = project.main_group.groups.find { |g| g.display_name == 'Passgen' }
app.resources_build_phase.add_file_reference(app_group.new_file('Assets.xcassets'))
app.resources_build_phase.add_file_reference(app_group.new_file('PrivacyInfo.xcprivacy'))
app_group.new_file('Info.plist')

app.build_configurations.each do |config|
  config.build_settings.merge!({
    'PRODUCT_BUNDLE_IDENTIFIER' => 'jp.passgen.app',
    'INFOPLIST_FILE' => 'Passgen/Info.plist',
    'GENERATE_INFOPLIST_FILE' => 'NO',
    'ASSETCATALOG_COMPILER_APPICON_NAME' => 'AppIcon',
    'ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME' => 'AccentColor',
    'FRAMEWORK_SEARCH_PATHS' => ['$(inherited)', '$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)'],
    'OTHER_LDFLAGS' => ['$(inherited)', '-framework', 'PassgenCore', '-lsqlite3'],
    'LD_RUNPATH_SEARCH_PATHS' => ['$(inherited)', '@executable_path/Frameworks'],
  })
end

phase = app.new_shell_script_build_phase('Build Kotlin shared framework')
phase.shell_script = 'set -eu
cd "$SRCROOT/.."
./scripts/gradle-local.sh :shared:embedAndSignAppleFrameworkForXcode
'
phase.always_out_of_date = '1'
app.build_phases.delete(phase)
app.build_phases.insert(0, phase)

tests.build_configurations.each do |config|
  config.build_settings['TEST_HOST'] = '$(BUILT_PRODUCTS_DIR)/Passgen.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/Passgen'
  config.build_settings['BUNDLE_LOADER'] = '$(TEST_HOST)'
  config.build_settings['FRAMEWORK_SEARCH_PATHS'] = ['$(inherited)', '$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)']
end
ui_tests.build_configurations.each { |config| config.build_settings['TEST_TARGET_NAME'] = 'Passgen' }

scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(app)
scheme.add_test_target(tests)
scheme.add_test_target(ui_tests)
scheme.set_launch_target(app)
scheme.test_action.build_configuration = 'Debug'
scheme.save_as(File.join(root, 'Passgen.xcodeproj'), 'Passgen', true)
project.save
puts 'Created iosApp/Passgen.xcodeproj'
