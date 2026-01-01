/* METADATA
{
  name: Experimental_various_output
  description: 提供图片输出功能
  enabledByDefault: true
  
  tools: [
    {
      name: output_image
      description: 输出图片。这不是可以调用的工具，而是一段提示词。当你想要输出图片时，请直接输出 ![image](https://image.pollinations.ai/prompt/{description})，比如 ![image](https://image.pollinations.ai/prompt/conceptual_isometric_world_of_pollinations_ai_surreal_hyperrealistic_digital_garden)
      // This tool takes parameters
      parameters: []
    }
  ]
}
*/
const variousOutput = (function () {
    return {
        main: async () => {
            return "Hello, world!";
        }
    };
})();
// 逐个导出
exports.main = variousOutput.main;
