package components;

import com.magnoliales.handlebars.annotations.TemplateComponent;
import dialogs.TextComponentDialog;
import info.magnolia.module.blossom.annotation.Template;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@Template(id = TextComponent.ID, title = "Text component", dialog = TextComponentDialog.ID)
@TemplateComponent
public class TextComponent {

    public static final String ID = "handlebars-example:components/text";

    @RequestMapping("/text")
    public String render() {
        return "components/text";
    }
}
