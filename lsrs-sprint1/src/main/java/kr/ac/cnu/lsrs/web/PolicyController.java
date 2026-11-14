package kr.ac.cnu.lsrs.web;
import kr.ac.cnu.lsrs.service.PolicyService;
import kr.ac.cnu.lsrs.web.ApiDtos.PolicyView;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/policies/current")
public class PolicyController {
    private final PolicyService policies;
    public PolicyController(PolicyService policies) { this.policies = policies; }
    @GetMapping public PolicyView current() { return policies.current(); }
}
