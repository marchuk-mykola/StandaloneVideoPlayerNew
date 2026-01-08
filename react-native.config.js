module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: './android/standalonevideoplayer',
        packageImportPath: 'import com.reactnativestandalonevideoplayer.StandaloneVideoPlayerPackage;',
        packageInstance: 'new StandaloneVideoPlayerPackage()',
      },
      ios: null, // iOS not implemented
    },
  },
};
