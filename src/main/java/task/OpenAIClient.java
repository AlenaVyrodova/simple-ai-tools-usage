package task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import task.dto.ChatCompletion;
import task.dto.Choice;
import task.dto.Function;
import task.dto.Message;
import task.dto.Model;
import task.dto.Role;
import task.dto.ToolCall;
import task.tools.HaikuGeneratorTool;
import task.tools.ImageStealerTool;
import task.tools.MathTool;
import task.tools.WebSearchTool;
import task.utils.Constant;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class OpenAIClient {
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final Model model;
    private final String apiKey;
    private final List<Map<String, Object>> tools;

    public OpenAIClient(Model model, String apiKey, List<Map<String, Object>> tools) {
        this.model = model;
        this.apiKey = checkApiKey(apiKey);
        this.tools = Objects.nonNull(tools) ? tools : new ArrayList<>();
        this.mapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
    }

    private String checkApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("apiKey cannot be null or empty");
        }
        return apiKey;
    }

    public Message responseWithMessage(List<Message> messages) throws Exception {
        Map<String,Object> requestBody = createRequest(messages);
        HttpRequest  httpRequest = generateRequest(requestBody);
        String response = httpClient.send(httpRequest,HttpResponse.BodyHandlers.ofString()).body();
        ChatCompletion chatCompletion = mapper.readValue(response,ChatCompletion.class);
        Choice choice = chatCompletion.choices().getFirst();
        Message message = choice.message();

        if(choice.finishReason().equals("tool_calls")){
            messages.add(message);
            processToolCalls(messages,message.getToolCalls());
            return responseWithMessage(messages);
        }
return  message;
    }

    private Map<String, Object> createRequest(List<Message> messages) {

       return Map.of(
               "model",this.model.getValue(),
               "messages",messages,
               "tools", this.tools
       );
    }

    private HttpRequest generateRequest(Map<String, Object> requestBody) throws JsonProcessingException {
        return  HttpRequest.newBuilder()
                .uri(Constant.OPEN_AI_API_URI)
                .header("Content-Type", "application/json")
                .header("Authorization","Bearer "+this.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();
    }

    private void processToolCalls(List<Message> messages, List<ToolCall> toolCalls) {
      toolCalls.parallelStream()
              .forEach(toolCall ->
              {
                  String toolCallId = toolCall.id();
                  String funcName =toolCall.function().name();
                  Map<String,Object> arguments = toolCall.function().arguments();
                  String toolResponse =executeTool(funcName,arguments);
                  messages.add(
                          Message.builder()
                                  .role(Role.TOOL)
                                  .toolCallId(toolCallId)
                                  .name(funcName)
                                  .content(toolResponse)
                                  .build()
                  );
              });
    }

    private String executeTool(String functionName, Map<String, Object> arguments) {
       return switch (functionName){
           case Constant.SIMPLE_CALCULATOR -> new MathTool().execute(arguments);
           case Constant.NASA_IMG_STEALER -> new ImageStealerTool(this.apiKey).execute(arguments);
           case Constant.WEB_SEARCH -> new WebSearchTool(this.apiKey).execute(arguments);
           case Constant.HAIKU_GENERATOR -> new HaikuGeneratorTool(this.apiKey).execute(arguments);
           default -> "Unknown function" + functionName;
       };

    }
}