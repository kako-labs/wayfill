import {existsSync} from 'node:fs';
import {Config} from '@remotion/cli/config';

Config.setVideoImageFormat('jpeg');
Config.setJpegQuality(92);
Config.setOverwriteOutput(true);

// Use the installed Chrome when Remotion's own headless shell cannot be downloaded.
const chrome = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
if (existsSync(chrome)) {
  Config.setBrowserExecutable(chrome);
  Config.setChromeMode('chrome-for-testing');
}
