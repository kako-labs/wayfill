import {Composition} from 'remotion';
import {Story, STORY_FRAMES, STORY_TTS_FRAMES} from './story/Story';

export const Root = () => (
  <>
    <Composition id="WayfillStory" component={Story} durationInFrames={STORY_FRAMES} fps={30} width={1920} height={1080} />
    <Composition id="WayfillStoryTts" component={Story} durationInFrames={STORY_TTS_FRAMES} fps={30} width={1920} height={1080}
      defaultProps={{narration: true}} />
  </>
);
