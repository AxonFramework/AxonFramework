package org.axonframework.examples.addressbook.web;

import org.axonframework.sample.app.command.ClaimedContactName;
import org.axonframework.sample.app.command.ContactNameRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * @author Jettro Coenradie
 */
@Controller
@RequestMapping(value = "/db")
public class ViewDatabaseController {
    @Autowired
    private ContactNameRepository repository;

    @RequestMapping("/claimed")
    public String claimedNames(Model model) {
        List<ClaimedContactName> claimedContactNames = repository.obtainAllClaimedNames();
        model.addAttribute("claimedNames", claimedContactNames);
        return "db/claimed";
    }
}
